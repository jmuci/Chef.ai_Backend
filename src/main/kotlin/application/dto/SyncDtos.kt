package com.tenmilelabs.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class SyncPushRequest(
    val recipes: List<SyncRecipe> = emptyList(),
    val bookmarkedRecipes: List<SyncBookmarkedRecipe> = emptyList()
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
    val serverTimestamp: Long,
    /** Reference entities required to resolve every [ConflictEntity.serverVersion] locally. */
    val referenceData: SyncReferenceData,
    val acceptedBookmarks: List<SyncBookmarkedRecipe> = emptyList(),
    val bookmarkErrors: List<BookmarkSyncError> = emptyList()
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
data class SyncAllergen(
    val uuid: String,
    val displayName: String,
    val updatedAt: Long,
    val deletedAt: Long?
)

@Serializable
data class SyncSourceClassification(
    val uuid: String,
    val category: String,
    val subcategory: String?,
    val updatedAt: Long,
    val deletedAt: Long?
)

@Serializable
data class SyncTag(
    val uuid: String,
    val displayName: String,
    val updatedAt: Long,
    val deletedAt: Long?
)

@Serializable
data class SyncLabel(
    val uuid: String,
    val displayName: String,
    val updatedAt: Long,
    val deletedAt: Long?
)

/**
 * All reference entities needed to satisfy FK constraints when persisting
 * a set of recipe aggregates. Shared by both the pull response (flat fields)
 * and the push response ([SyncPushResponse.referenceData]).
 */
@Serializable
data class SyncReferenceData(
    val ingredients: List<SyncIngredient> = emptyList(),
    val allergens: List<SyncAllergen> = emptyList(),
    val sourceClassifications: List<SyncSourceClassification> = emptyList(),
    val tags: List<SyncTag> = emptyList(),
    val labels: List<SyncLabel> = emptyList()
)

@Serializable
data class SyncPullResponse(
    val recipes: List<SyncRecipe>,
    val ingredients: List<SyncIngredient>,
    val allergens: List<SyncAllergen>,
    val sourceClassifications: List<SyncSourceClassification>,
    val tags: List<SyncTag>,
    val labels: List<SyncLabel>,
    val bookmarkedRecipes: List<SyncBookmarkedRecipe> = emptyList(),
    val serverTimestamp: Long,
    val hasMore: Boolean
)

@Serializable
data class SyncBookmarkedRecipe(
    val userId: String,
    val recipeId: String,
    /** Epoch millis. On push responses this reflects the server-assigned timestamp. */
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /** Present in push requests/responses; null in pull responses. */
    val syncState: String? = null
)

@Serializable
data class BookmarkSyncError(
    val userId: String,
    val recipeId: String,
    val reason: BookmarkSyncErrors,
    val message: String
)

@Serializable
enum class BookmarkSyncErrors(val message: String) {
    INVALID_USER_ID("userId is invalid"),
    INVALID_RECIPE_ID("recipeId is invalid"),
    USER_MISMATCH("userId does not match authenticated user"),
    RECIPE_NOT_FOUND("Recipe does not exist"),
    RECIPE_ACCESS_DENIED("Cannot bookmark a private recipe you do not own")
}
