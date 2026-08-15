package com.tenmilelabs.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class RecipeSearchResponse(
    val query: String,
    val results: List<RecipeSearchResultDto>,
    val hasMore: Boolean,
)

// Deliberately no defaults on any field: encodeDefaults = false (Routing.kt) means a defaulted
// field silently vanishes from the JSON when it holds its default value, so a client bug reading
// a missing field would just see its own Kotlin default instead of failing to deserialize.
@Serializable
data class RecipeSearchResultDto(
    val uuid: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val imageUrlThumbnail: String,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val servings: Int,
    val creatorId: String,
    val privacy: String,
    val updatedAt: Long,
    val tags: List<SidecarTagDto>,
    val labels: List<SidecarLabelDto>,
)
