package com.wheredidiputit.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItemDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String?,
    @SerialName("location_text") val locationText: String,
    @SerialName("category_id") val categoryId: String?,
    @SerialName("image_url") val imagePath: String?,
    @SerialName("is_favorite") val isFavorite: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String?,
)

@Serializable
data class CategoryDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String?,
    @SerialName("name") val name: String,
    @SerialName("created_at") val createdAt: String,
)
