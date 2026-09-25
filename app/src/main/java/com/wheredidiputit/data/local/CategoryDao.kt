package com.wheredidiputit.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wheredidiputit.domain.model.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE user_id = :userId ORDER BY name COLLATE NOCASE ASC")
    fun observeForUser(userId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE user_id = :userId AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(userId: String, name: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Upsert
    suspend fun upsert(entity: CategoryEntity)

    @Query("SELECT * FROM categories WHERE user_id = :userId AND sync_state != 'SYNCED'")
    suspend fun getUnsynced(userId: String): List<CategoryEntity>

    @Query("UPDATE categories SET sync_state = :state WHERE id = :id")
    suspend fun setSyncState(id: String, state: SyncState)

    @Query("DELETE FROM categories WHERE user_id = :userId AND sync_state = 'SYNCED' AND id NOT IN (:keepIds)")
    suspend fun deleteSyncedExcept(userId: String, keepIds: List<String>)

    @Query("DELETE FROM categories WHERE user_id = :userId AND sync_state = 'SYNCED'")
    suspend fun deleteAllSynced(userId: String)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
