package com.wheredidiputit.data.repository

import com.wheredidiputit.data.local.CategoryDao
import com.wheredidiputit.data.local.CategoryEntity
import com.wheredidiputit.domain.model.AppError
import com.wheredidiputit.domain.model.AppResult
import com.wheredidiputit.domain.model.BuiltInCategories
import com.wheredidiputit.domain.model.Category
import com.wheredidiputit.domain.model.ItemLimits
import com.wheredidiputit.domain.model.SyncState
import com.wheredidiputit.domain.repository.AuthRepository
import com.wheredidiputit.domain.repository.CategoryRepository
import com.wheredidiputit.domain.repository.SyncController
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val authRepository: AuthRepository,
    private val syncController: SyncController,
) : CategoryRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCategories(): Flow<List<Category>> =
        authRepository.userIdFlow.flatMapLatest { userId ->
            if (userId == null) {
                flowOf(BuiltInCategories.all)
            } else {
                categoryDao.observeForUser(userId).map { custom ->
                    BuiltInCategories.all + custom.map { it.toDomain() }
                }
            }
        }

    override suspend fun createCategory(name: String): AppResult<Category> {
        val userId = authRepository.currentUserId ?: return AppResult.Failure(AppError.SESSION_EXPIRED)
        val trimmed = name.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty() || trimmed.length > ItemLimits.CATEGORY_NAME_MAX) {
            return AppResult.Failure(AppError.UNKNOWN)
        }
        BuiltInCategories.all.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            ?.let { return AppResult.Success(it) }

        return try {
            categoryDao.findByName(userId, trimmed)?.let { return AppResult.Success(it.toDomain()) }
            val entity = CategoryEntity(
                id = UUID.randomUUID().toString().lowercase(Locale.ROOT),
                userId = userId,
                name = trimmed,
                createdAt = System.currentTimeMillis(),
                syncState = SyncState.PENDING_CREATE,
            )
            categoryDao.upsert(entity)
            syncController.requestSync()
            AppResult.Success(entity.toDomain())
        } catch (e: android.database.SQLException) {
            AppResult.Failure(AppError.STORAGE)
        }
    }
}
