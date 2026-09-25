package com.wheredidiputit.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query(
        "SELECT * FROM items WHERE user_id = :userId AND deleted_at IS NULL " +
            "ORDER BY updated_at DESC",
    )
    fun observeActive(userId: String): Flow<List<ItemEntity>>

    @Query(
        "SELECT * FROM items WHERE user_id = :userId AND deleted_at IS NULL AND is_favorite = 1 " +
            "ORDER BY updated_at DESC",
    )
    fun observeFavorites(userId: String): Flow<List<ItemEntity>>

    /** [pattern] must already be folded and LIKE-escaped. */
    @Query(
        "SELECT * FROM items WHERE user_id = :userId AND deleted_at IS NULL " +
            "AND search_fold LIKE '%' || :pattern || '%' ESCAPE '\\' " +
            "ORDER BY CASE " +
            "WHEN title_fold LIKE :pattern || '%' ESCAPE '\\' THEN 0 " +
            "WHEN title_fold LIKE '%' || :pattern || '%' ESCAPE '\\' THEN 1 " +
            "ELSE 2 END, updated_at DESC " +
            "LIMIT 200",
    )
    fun search(userId: String, pattern: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id AND user_id = :userId AND deleted_at IS NULL")
    fun observeById(userId: String, id: String): Flow<ItemEntity?>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: String): ItemEntity?

    @Upsert
    suspend fun upsert(entity: ItemEntity)

    @Query("SELECT * FROM items WHERE user_id = :userId AND sync_state != 'SYNCED' ORDER BY updated_at ASC")
    suspend fun getUnsynced(userId: String): List<ItemEntity>

    @Query("SELECT COUNT(*) FROM items WHERE user_id = :userId AND sync_state != 'SYNCED'")
    suspend fun countUnsynced(userId: String): Int

    @Query("SELECT COUNT(*) FROM items WHERE user_id = :userId AND sync_state = 'FAILED' AND deleted_at IS NULL")
    fun observeFailedCount(userId: String): Flow<Int>

    /**
     * Marks a pushed record as synced, but only if nobody edited it while the
     * upload was in flight. Returns the number of rows changed.
     */
    @Query(
        "UPDATE items SET sync_state = 'SYNCED', remote_image_path = :remoteImagePath, " +
            "pending_image_upload = 0, pending_image_removal = 0 " +
            "WHERE id = :id AND updated_at = :expectedUpdatedAt",
    )
    suspend fun markSynced(id: String, expectedUpdatedAt: Long, remoteImagePath: String?): Int

    /** Records which photo the cloud row points to after a push that raced with a local edit. */
    @Query("UPDATE items SET remote_image_path = :remoteImagePath WHERE id = :id")
    suspend fun setRemoteImagePath(id: String, remoteImagePath: String?)

    @Query("UPDATE items SET pending_image_upload = 0 WHERE id = :id AND local_image_path = :localImagePath")
    suspend fun clearPendingUploadIfSameLocal(id: String, localImagePath: String)

    @Query(
        "SELECT * FROM items WHERE user_id = :userId AND sync_state IN " +
            "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE') ORDER BY updated_at ASC",
    )
    suspend fun getPending(userId: String): List<ItemEntity>

    @Query("UPDATE items SET sync_state = 'FAILED' WHERE id = :id AND updated_at = :expectedUpdatedAt")
    suspend fun markFailed(id: String, expectedUpdatedAt: Long): Int

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM items WHERE id = :id AND updated_at = :expectedUpdatedAt")
    suspend fun deleteIfUnchanged(id: String, expectedUpdatedAt: Long): Int

    @Query(
        "SELECT * FROM items WHERE user_id = :userId AND deleted_at IS NULL " +
            "AND remote_image_path IS NOT NULL AND local_image_path IS NULL LIMIT :limit",
    )
    suspend fun getMissingImages(userId: String, limit: Int): List<ItemEntity>

    @Query(
        "UPDATE items SET local_image_path = :localImagePath " +
            "WHERE id = :id AND remote_image_path = :remoteImagePath AND local_image_path IS NULL",
    )
    suspend fun attachDownloadedImage(id: String, remoteImagePath: String, localImagePath: String): Int

    @Query("DELETE FROM items")
    suspend fun deleteAll()
}
