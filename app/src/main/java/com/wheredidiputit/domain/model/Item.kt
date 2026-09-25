package com.wheredidiputit.domain.model

import java.time.Instant

/** A single remembered thing and where it was put. */
data class Item(
    val id: String,
    val title: String,
    val location: String,
    val description: String?,
    val category: Category?,
    /** Absolute path of the photo stored on this device, if one is available. */
    val localImagePath: String?,
    /** True when a photo exists in the cloud but has not been downloaded yet. */
    val hasPendingImageDownload: Boolean,
    val isFavorite: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncState: SyncState,
)

enum class SyncState {
    SYNCED,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
    FAILED,
}

data class Category(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean,
)

/** What the person entered on the Remember screen. */
data class ItemDraft(
    val title: String,
    val location: String,
    val description: String?,
    val categoryId: String?,
    val photo: PhotoChange,
)

sealed interface PhotoChange {
    data object Keep : PhotoChange
    data object Remove : PhotoChange

    /** A processed, EXIF-free JPEG waiting in the app's private staging area. */
    data class Replace(val stagedFilePath: String) : PhotoChange
}

object ItemLimits {
    const val TITLE_MAX = 120
    const val LOCATION_MAX = 200
    const val DESCRIPTION_MAX = 1000
    const val CATEGORY_NAME_MAX = 40
}
