package com.wheredidiputit.data.sync

import androidx.room.withTransaction
import com.wheredidiputit.core.config.AppConfig
import com.wheredidiputit.data.image.ImageStore
import com.wheredidiputit.data.local.CategoryDao
import com.wheredidiputit.data.local.CategoryEntity
import com.wheredidiputit.data.local.ItemDao
import com.wheredidiputit.data.local.ItemEntity
import com.wheredidiputit.data.local.WdipiDatabase
import com.wheredidiputit.data.preferences.UserPreferences
import com.wheredidiputit.data.remote.CategoryDto
import com.wheredidiputit.data.remote.ItemDto
import com.wheredidiputit.data.remote.SupabaseProvider
import com.wheredidiputit.data.remote.isTransient
import com.wheredidiputit.data.remote.safeCall
import com.wheredidiputit.data.repository.parseTimestamp
import com.wheredidiputit.data.repository.searchFolds
import com.wheredidiputit.data.repository.toDto
import com.wheredidiputit.domain.model.SyncState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.BucketApi
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.io.File
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Two-way sync between Room (what the person sees) and Supabase (the cloud
 * source of truth).
 *
 * Push: every local change is written with an upsert keyed by the item's UUID,
 * so retries are idempotent. Deletes are soft (`deleted_at`).
 * Pull: rows changed since the last pull are applied unless the same record
 * still has unsent local changes, in which case the local edit wins and is
 * pushed on the next pass.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val provider: SupabaseProvider,
    private val database: WdipiDatabase,
    private val itemDao: ItemDao,
    private val categoryDao: CategoryDao,
    private val imageStore: ImageStore,
    private val preferences: UserPreferences,
) {
    enum class Outcome { SUCCESS, RETRY }

    private val mutex = Mutex()

    suspend fun sync(): Outcome = mutex.withLock { syncLocked() }

    private suspend fun syncLocked(): Outcome {
        val client = provider.client ?: return Outcome.SUCCESS
        client.auth.awaitInitialization()
        val status = client.auth.sessionStatus.value
        if (status is SessionStatus.RefreshFailure) return Outcome.RETRY
        val userId = (status as? SessionStatus.Authenticated)?.session?.user?.id ?: return Outcome.SUCCESS

        try {
            pushCategories(client, userId)
            pushItems(client, userId, itemDao.getUnsynced(userId))
            // Pick up edits made while the first pass was uploading.
            repeat(MAX_EXTRA_PASSES) {
                val pending = itemDao.getPending(userId)
                if (pending.isEmpty()) return@repeat
                pushItems(client, userId, pending)
            }
            pullCategories(client, userId)
            pullItems(client, userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return if (e.isTransient()) Outcome.RETRY else Outcome.SUCCESS
        }

        // Photo downloads are best effort; missing ones are retried on the next sync.
        safeCall { downloadMissingImages(client, userId) }
        return Outcome.SUCCESS
    }

    // ---- Push -------------------------------------------------------------

    private suspend fun pushCategories(client: SupabaseClient, userId: String) {
        for (category in categoryDao.getUnsynced(userId)) {
            safeCall {
                client.from(TABLE_CATEGORIES).upsert(category.toDto()) { onConflict = "id" }
            }.onSuccess {
                categoryDao.setSyncState(category.id, SyncState.SYNCED)
            }.onFailure { error ->
                if (error.isTransient()) throw error
                categoryDao.setSyncState(category.id, SyncState.FAILED)
            }
        }
    }

    private suspend fun pushItems(client: SupabaseClient, userId: String, items: List<ItemEntity>) {
        val bucket = client.storage.from(AppConfig.PHOTO_BUCKET)
        for (item in items) {
            safeCall { pushItem(client, bucket, userId, item) }.onFailure { error ->
                if (error.isTransient()) throw error
                // Rejected by the server (validation, permissions): keep it on
                // the device, mark it so the person can see it did not sync.
                itemDao.markFailed(item.id, item.updatedAt)
            }
        }
    }

    private suspend fun pushItem(client: SupabaseClient, bucket: BucketApi, userId: String, item: ItemEntity) {
        if (item.deletedAt != null) {
            client.from(TABLE_ITEMS).upsert(item.toDto(userId, imagePath = null)) { onConflict = "id" }
            item.remoteImagePath?.let { path -> safeCall { bucket.delete(path) } }
            itemDao.deleteIfUnchanged(item.id, item.updatedAt)
            return
        }

        val previousRemote = item.remoteImagePath
        var remotePath = previousRemote
        val localImage = item.localImagePath
        if (item.pendingImageUpload && localImage != null) {
            val bytes = imageStore.read(localImage)
            if (bytes != null) {
                // Path is derived from the local file name, so a retried upload overwrites itself.
                val path = "users/$userId/items/${item.id}/${File(localImage).name}"
                bucket.upload(path, bytes) {
                    upsert = true
                    contentType = ContentType.Image.JPEG
                }
                remotePath = path
            }
        } else if (item.pendingImageRemoval) {
            remotePath = null
        }

        client.from(TABLE_ITEMS).upsert(item.toDto(userId, remotePath)) { onConflict = "id" }

        if (previousRemote != null && previousRemote != remotePath) {
            safeCall { bucket.delete(previousRemote) }
        }

        val updated = itemDao.markSynced(item.id, item.updatedAt, remotePath)
        if (updated == 0) {
            val current = itemDao.getById(item.id)
            if (current == null) {
                // Deleted on this device while its first upload was in flight.
                client.from(TABLE_ITEMS).upsert(
                    item.copy(deletedAt = System.currentTimeMillis()).toDto(userId, imagePath = null),
                ) { onConflict = "id" }
                remotePath?.let { path -> safeCall { bucket.delete(path) } }
            } else {
                // Edited meanwhile: remember what the cloud now references; the edit is pushed next pass.
                itemDao.setRemoteImagePath(item.id, remotePath)
                if (localImage != null && remotePath != previousRemote) {
                    itemDao.clearPendingUploadIfSameLocal(item.id, localImage)
                }
            }
        }
    }

    // ---- Pull -------------------------------------------------------------

    private suspend fun pullCategories(client: SupabaseClient, userId: String) {
        val remote = client.from(TABLE_CATEGORIES).select {
            filter { eq("user_id", userId) }
        }.decodeList<CategoryDto>()

        database.withTransaction {
            for (dto in remote) {
                val local = categoryDao.getById(dto.id)
                if (local != null && local.syncState != SyncState.SYNCED) continue
                categoryDao.upsert(
                    CategoryEntity(
                        id = dto.id,
                        userId = userId,
                        name = dto.name,
                        createdAt = dto.createdAt.parseTimestamp(),
                        syncState = SyncState.SYNCED,
                    ),
                )
            }
            val remoteIds = remote.map { it.id }
            if (remoteIds.isEmpty()) {
                categoryDao.deleteAllSynced(userId)
            } else {
                categoryDao.deleteSyncedExcept(userId, remoteIds)
            }
        }
    }

    private suspend fun pullItems(client: SupabaseClient, userId: String) {
        val cursor = preferences.lastPulledAt(userId)
        // Small overlap guards against rows committed slightly out of order.
        val since = cursor?.let { OffsetDateTime.parse(it).minusMinutes(CURSOR_OVERLAP_MINUTES).toInstant().toString() }
        var newest: OffsetDateTime? = cursor?.let { OffsetDateTime.parse(it) }
        var offset = 0L

        while (true) {
            val page = client.from(TABLE_ITEMS).select {
                filter {
                    eq("user_id", userId)
                    if (since != null) gte("updated_at", since)
                }
                order("updated_at", Order.ASCENDING)
                range(offset, offset + PAGE_SIZE - 1)
            }.decodeList<ItemDto>()

            for (dto in page) {
                applyRemoteItem(dto, userId)
                val updatedAt = OffsetDateTime.parse(dto.updatedAt)
                if (newest == null || updatedAt.isAfter(newest)) newest = updatedAt
            }
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }

        newest?.let { preferences.setLastPulledAt(userId, it.toString()) }
    }

    private suspend fun applyRemoteItem(dto: ItemDto, userId: String) {
        val obsoleteFile = database.withTransaction {
            val local = itemDao.getById(dto.id)
            if (local != null && local.syncState != SyncState.SYNCED) return@withTransaction null

            if (dto.deletedAt != null) {
                if (local != null) itemDao.delete(dto.id)
                return@withTransaction local?.localImagePath
            }

            val imageChanged = local != null && local.remoteImagePath != dto.imagePath
            val (titleFold, searchFold) = searchFolds(dto.title, dto.locationText, dto.description)
            itemDao.upsert(
                ItemEntity(
                    id = dto.id,
                    userId = userId,
                    title = dto.title,
                    description = dto.description,
                    locationText = dto.locationText,
                    categoryId = dto.categoryId,
                    remoteImagePath = dto.imagePath,
                    localImagePath = if (imageChanged) null else local?.localImagePath,
                    pendingImageUpload = false,
                    pendingImageRemoval = false,
                    isFavorite = dto.isFavorite,
                    createdAt = dto.createdAt.parseTimestamp(),
                    updatedAt = dto.updatedAt.parseTimestamp(),
                    deletedAt = null,
                    syncState = SyncState.SYNCED,
                    titleFold = titleFold,
                    searchFold = searchFold,
                ),
            )
            if (imageChanged) local?.localImagePath else null
        }
        imageStore.delete(obsoleteFile)
    }

    /** Photos are cached on the device so memories stay complete offline. */
    private suspend fun downloadMissingImages(client: SupabaseClient, userId: String) {
        val bucket = client.storage.from(AppConfig.PHOTO_BUCKET)
        for (item in itemDao.getMissingImages(userId, IMAGE_DOWNLOAD_BATCH)) {
            val remotePath = item.remoteImagePath ?: continue
            val bytes = safeCall { bucket.downloadAuthenticated(remotePath) }.getOrNull() ?: continue
            val localPath = imageStore.saveDownloaded(item.id, bytes)
            if (itemDao.attachDownloadedImage(item.id, remotePath, localPath) == 0) {
                imageStore.delete(localPath)
            }
        }
    }

    private companion object {
        const val TABLE_ITEMS = "items"
        const val TABLE_CATEGORIES = "categories"
        const val PAGE_SIZE = 500L
        const val MAX_EXTRA_PASSES = 2
        const val CURSOR_OVERLAP_MINUTES = 2L
        const val IMAGE_DOWNLOAD_BATCH = 40
    }
}
