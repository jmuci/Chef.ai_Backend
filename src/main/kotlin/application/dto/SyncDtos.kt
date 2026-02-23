package com.tenmilelabs.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class SyncPushRequest(
    val recipes: List<SyncRecipe>
)

@Serializable
data class SyncRecipe(
    val uuid: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val imageUrlThumbnail: String,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val servings: Int,
    val creatorId: String,
    val recipeExternalUrl: String?,
    val privacy: String,
    val updatedAt: Long,
    val deletedAt: Long?,
    val steps: List<SyncRecipeStep>,
    val ingredients: List<SyncRecipeIngredient>,
    val tagIds: List<String>,
    val labelIds: List<String>
)

@Serializable
data class SyncRecipeStep(
    val uuid: String,
    val orderIndex: Int,
    val instruction: String
)

@Serializable
data class SyncRecipeIngredient(
    val ingredientId: String,
    val quantity: Double,
    val unit: String
)

@Serializable
data class SyncPushResponse(
    val accepted: List<AcceptedEntity>,
    val conflicts: List<ConflictEntity>,
    val errors: List<SyncError>,
    val serverTimestamp: Long
)

@Serializable
data class AcceptedEntity(
    val uuid: String,
    val serverUpdatedAt: Long
)

@Serializable
data class ConflictEntity(
    val uuid: String,
    val reason: String,
    val serverVersion: SyncRecipe
)

@Serializable
data class SyncError(
    val uuid: String,
    val reason: String,
    val message: String
)

@Serializable
data class SyncPullResponse(
    val recipes: List<SyncRecipe>,
    val serverTimestamp: Long,
    val hasMore: Boolean
)
