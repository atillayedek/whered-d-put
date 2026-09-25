package com.wheredidiputit.data.repository

import android.database.SQLException
import androidx.room.withTransaction
import com.wheredidiputit.data.image.ImageStore
import com.wheredidiputit.data.local.ItemDao
import com.wheredidiputit.data.local.ItemEntity
import com.wheredidiputit.data.local.WdipiDatabase
import com.wheredidiputit.domain.model.AppError
import com.wheredidiputit.domain.model.AppResult
import com.wheredidiputit.domain.model.Item
import com.wheredidiputit.domain.model.ItemDraft
import com.wheredidiputit.domain.model.ItemLimits
import com.wheredidiputit.domain.model.PhotoChange
import com.wheredidiputit.domain.model.SyncState
import com.wheredidiputit.domain.repository.AuthRepository
import com.wheredidiputit.domain.repository.CategoryRepository
import com.wheredidiputit.domain.repository.ItemRepository
import com.wheredidiputit.domain.repository.SyncController
import com.wheredidiputit.domain.util.SearchText
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Local-first: every write lands in Room immediately and the UI updates from
 * Room. Cloud sync happens afterwards in [com.wheredidiputit.data.sync.SyncWorker].
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class ItemRepositoryImpl @Inject constructor(
    private val database: WdipiDatabase,
    private val itemDao: ItemDao,
    private val authRepository: AuthRepository,
    private val categoryRepository: CategoryRepository,
    private val imageStore: ImageStore,
    private val syncController: SyncController,
) : ItemRepository {

    private fun items(query: (userId: String) -> Flow<List<ItemEntity>>): Flow<List<Item>> =
        authRepository.userIdFlow.flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                combine(query(userId), categoryRepository.observeCategories()) { entities, categories ->
                    val byId = categories.associateBy { it.id }
                    entities.map { it.toDomain(byId) }
                }
            }
        }

    override fun observeRecent(): Flow<List<Item>> = items(itemDao::observeActive)

    override fun observeFavorites(): Flow<List<Item>> = items(itemDao::observeFavorites)

    override fun search(query: String): Flow<List<Item>> {
        val folded = SearchText.fold(query)
        if (folded.isEmpty()) return observeRecent()
        val pattern = SearchText.escapeLike(folded)
        return items { userId -> itemDao.search(userId, pattern) }
    }

    override fun observeItem(id: String): Flow<Item?> =
        authRepository.userIdFlow.flatMapLatest { userId ->
            if (userId == null) {
                flowOf(null)
            } else {
                combine(itemDao.observeById(userId, id), categoryRepository.observeCategories()) { entity, categories ->
                    entity?.toDomain(categories.associateBy { it.id })
                }
            }
        }

    override fun observeFailedSyncCount(): Flow<Int> =
        authRepository.userIdFlow.flatMapLatest { userId ->
            if (userId == null) flowOf(0) else itemDao.observeFailedCount(userId)
        }

    override suspend fun create(draft: ItemDraft): AppResult<String> = write {
        val userId = authRepository.currentUserId ?: return@write AppResult.Failure(AppError.SESSION_EXPIRED)
        val clean = draft.sanitized() ?: return@write AppResult.Failure(AppError.UNKNOWN)
        val id = UUID.randomUUID().toString().lowercase(Locale.ROOT)
        val now = System.currentTimeMillis()
        val localImage = (clean.photo as? PhotoChange.Replace)?.let { imageStore.adoptStaged(it.stagedFilePath, id) }
        val (titleFold, searchFold) = searchFolds(clean.title, clean.location, clean.description)
        itemDao.upsert(
            ItemEntity(
                id = id,
                userId = userId,
                title = clean.title,
                description = clean.description,
                locationText = clean.location,
                categoryId = clean.categoryId,
                remoteImagePath = null,
                localImagePath = localImage,
                pendingImageUpload = localImage != null,
                pendingImageRemoval = false,
                isFavorite = false,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                syncState = SyncState.PENDING_CREATE,
                titleFold = titleFold,
                searchFold = searchFold,
            ),
        )
        AppResult.Success(id)
    }

    override suspend fun update(id: String, draft: ItemDraft): AppResult<Unit> = write {
        val clean = draft.sanitized() ?: return@write AppResult.Failure(AppError.UNKNOWN)
        val outcome = database.withTransaction {
            val existing = itemDao.getById(id)
            if (existing == null || existing.deletedAt != null) return@withTransaction UpdateOutcome(found = false, obsoleteFile = null)
            var localImage = existing.localImagePath
            var pendingUpload = existing.pendingImageUpload
            var pendingRemoval = existing.pendingImageRemoval
            val obsoleteFile: String?
            when (val photo = clean.photo) {
                PhotoChange.Keep -> obsoleteFile = null
                PhotoChange.Remove -> {
                    obsoleteFile = existing.localImagePath
                    localImage = null
                    pendingUpload = false
                    pendingRemoval = existing.remoteImagePath != null
                }
                is PhotoChange.Replace -> {
                    val adopted = imageStore.adoptStaged(photo.stagedFilePath, id)
                    obsoleteFile = if (adopted != null) existing.localImagePath else null
                    if (adopted != null) {
                        localImage = adopted
                        pendingUpload = true
                        pendingRemoval = false
                    }
                }
            }
            val (titleFold, searchFold) = searchFolds(clean.title, clean.location, clean.description)
            itemDao.upsert(
                existing.copy(
                    title = clean.title,
                    description = clean.description,
                    locationText = clean.location,
                    categoryId = clean.categoryId,
                    localImagePath = localImage,
                    pendingImageUpload = pendingUpload,
                    pendingImageRemoval = pendingRemoval,
                    updatedAt = nextTimestamp(existing.updatedAt),
                    syncState = existing.syncState.afterLocalEdit(),
                    titleFold = titleFold,
                    searchFold = searchFold,
                ),
            )
            UpdateOutcome(found = true, obsoleteFile = obsoleteFile)
        }
        if (!outcome.found) return@write AppResult.Failure(AppError.NOT_FOUND)
        imageStore.delete(outcome.obsoleteFile)
        AppResult.Success(Unit)
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        write {
            database.withTransaction {
                val existing = itemDao.getById(id) ?: return@withTransaction
                if (existing.deletedAt != null || existing.isFavorite == favorite) return@withTransaction
                itemDao.upsert(
                    existing.copy(
                        isFavorite = favorite,
                        updatedAt = nextTimestamp(existing.updatedAt),
                        syncState = existing.syncState.afterLocalEdit(),
                    ),
                )
            }
            AppResult.Success(Unit)
        }
    }

    override suspend fun delete(id: String) {
        write {
            val fileToRemove = database.withTransaction {
                val existing = itemDao.getById(id) ?: return@withTransaction null
                if (existing.syncState == SyncState.PENDING_CREATE) {
                    // Never reached the cloud: nothing to tell the server.
                    itemDao.delete(id)
                } else {
                    itemDao.upsert(
                        existing.copy(
                            deletedAt = System.currentTimeMillis(),
                            updatedAt = nextTimestamp(existing.updatedAt),
                            localImagePath = null,
                            pendingImageUpload = false,
                            syncState = SyncState.PENDING_DELETE,
                        ),
                    )
                }
                existing.localImagePath
            }
            imageStore.delete(fileToRemove)
            AppResult.Success(Unit)
        }
    }

    override suspend fun unsyncedCount(): Int {
        val userId = authRepository.currentUserId ?: return 0
        return itemDao.countUnsynced(userId)
    }

    /** Runs a local write, then asks for a background sync. */
    private suspend fun <T> write(block: suspend () -> AppResult<T>): AppResult<T> {
        val result = try {
            block()
        } catch (e: SQLException) {
            AppResult.Failure(AppError.STORAGE)
        }
        if (result is AppResult.Success) syncController.requestSync()
        return result
    }

    private class UpdateOutcome(val found: Boolean, val obsoleteFile: String?)

    private fun SyncState.afterLocalEdit(): SyncState =
        if (this == SyncState.PENDING_CREATE) SyncState.PENDING_CREATE else SyncState.PENDING_UPDATE

    /** Guarantees a new value so in-flight uploads can detect concurrent edits. */
    private fun nextTimestamp(previous: Long): Long = max(System.currentTimeMillis(), previous + 1)

    private fun ItemDraft.sanitized(): ItemDraft? {
        val cleanTitle = title.trim().take(ItemLimits.TITLE_MAX)
        val cleanLocation = location.trim().take(ItemLimits.LOCATION_MAX)
        if (cleanTitle.isEmpty() || cleanLocation.isEmpty()) return null
        return copy(
            title = cleanTitle,
            location = cleanLocation,
            description = description?.trim()?.take(ItemLimits.DESCRIPTION_MAX)?.ifEmpty { null },
        )
    }
}
