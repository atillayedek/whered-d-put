package com.wheredidiputit.data.repository

import com.wheredidiputit.data.local.CategoryEntity
import com.wheredidiputit.data.local.ItemEntity
import com.wheredidiputit.data.remote.CategoryDto
import com.wheredidiputit.data.remote.ItemDto
import com.wheredidiputit.domain.model.Category
import com.wheredidiputit.domain.model.Item
import com.wheredidiputit.domain.util.SearchText
import java.time.Instant
import java.time.OffsetDateTime

internal fun ItemEntity.toDomain(categories: Map<String, Category>): Item = Item(
    id = id,
    title = title,
    location = locationText,
    description = description,
    category = categoryId?.let { categories[it] },
    localImagePath = localImagePath,
    hasPendingImageDownload = remoteImagePath != null && localImagePath == null && !pendingImageRemoval,
    isFavorite = isFavorite,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    syncState = syncState,
)

internal fun CategoryEntity.toDomain(): Category = Category(id = id, name = name, isBuiltIn = false)

internal fun ItemEntity.toDto(userId: String, imagePath: String?): ItemDto = ItemDto(
    id = id,
    userId = userId,
    title = title,
    description = description,
    locationText = locationText,
    categoryId = categoryId,
    imagePath = imagePath,
    isFavorite = isFavorite,
    createdAt = createdAt.toIso(),
    updatedAt = updatedAt.toIso(),
    deletedAt = deletedAt?.toIso(),
)

internal fun CategoryEntity.toDto(): CategoryDto = CategoryDto(
    id = id,
    userId = userId,
    name = name,
    createdAt = createdAt.toIso(),
)

internal fun searchFolds(title: String, location: String, description: String?): Pair<String, String> {
    val titleFold = SearchText.fold(title)
    val all = SearchText.fold(listOfNotNull(title, location, description).joinToString(" "))
    return titleFold to all
}

internal fun Long.toIso(): String = Instant.ofEpochMilli(this).toString()

/** Postgres returns offsets like `+00:00` and microseconds; parse them safely. */
internal fun String.parseTimestamp(): Long = OffsetDateTime.parse(this).toInstant().toEpochMilli()
