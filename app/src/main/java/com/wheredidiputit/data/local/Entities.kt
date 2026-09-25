package com.wheredidiputit.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wheredidiputit.domain.model.SyncState

@Entity(
    tableName = "items",
    indices = [
        Index(value = ["user_id", "deleted_at", "updated_at"]),
        Index(value = ["user_id", "sync_state"]),
    ],
)
data class ItemEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "location_text") val locationText: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    /** Storage object path the cloud row currently points to. */
    @ColumnInfo(name = "remote_image_path") val remoteImagePath: String?,
    /** Private file on this device used for display. */
    @ColumnInfo(name = "local_image_path") val localImagePath: String?,
    @ColumnInfo(name = "pending_image_upload") val pendingImageUpload: Boolean,
    @ColumnInfo(name = "pending_image_removal") val pendingImageRemoval: Boolean,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    /** Epoch milliseconds, UTC. */
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
    @ColumnInfo(name = "sync_state") val syncState: SyncState,
    /** Folded copy of the title used for ranking search results. */
    @ColumnInfo(name = "title_fold") val titleFold: String,
    /** Folded title + location + description used for matching. */
    @ColumnInfo(name = "search_fold") val searchFold: String,
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["user_id"])],
)
data class CategoryEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "sync_state") val syncState: SyncState,
)
