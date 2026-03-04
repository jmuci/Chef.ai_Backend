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
    val reason: ConflictReasons,
    val serverVersion: SyncRecipe
)

@Serializable
enum class ConflictReasons {
    SERVER_NEWER
}

@Serializable
data class SyncError(
    val uuid: String,
    val reason: SyncErrors,
    val message: String
)

@Serializable
enum class SyncErrors(val message: String) {
    INVALID_UUID("Recipe UUID is invalid"),
    INVALID_CREATOR("creatorId is invalid"),
    CREATOR_MISMATCH("creatorId does not match authenticated user"),
    INVALID_PRIVACY("privacy must be PUBLIC or PRIVATE"),
    INVALID_INGREDIENT("ingredientId is invalid"),
    INGREDIENT_NOT_FOUND("ingredientId does not exist"),
    INVALID_TAG("tagId is invalid"),
    INVALID_LABEL("labelId is invalid")
}

@Serializable
data class SyncIngredient(
    val uuid: String,
    val displayName: String,
    val allergenId: String?,
    val sourcePrimaryId: String?,
    val updatedAt: Long,
    val deletedAt: Long?
)

@Serializable
data class SyncPullResponse(
    val recipes: List<SyncRecipe>,
    val ingredients: List<SyncIngredient>,
    val serverTimestamp: Long,
    val hasMore: Boolean
)
