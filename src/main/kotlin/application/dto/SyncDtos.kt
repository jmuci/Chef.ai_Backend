package com.tenmilelabs.application.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class SyncPushRequest(
    val recipes: List<SyncRecipe>,
    val bookmarkedRecipes: List<SyncBookmark> = emptyList(),
    val mealPlans: List<SyncMealPlanDto> = emptyList(),
    val groceryListItems: List<SyncGroceryListItem> = emptyList()
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
    val labelIds: List<String>,
    /**
     * Content hash of the uploaded hero image blob, or null if none. Set exclusively by
     * `PUT /recipes/{id}/image` and cleared exclusively by `DELETE /recipes/{id}/image` —
     * a client-supplied value is always ignored on push and the server's own value echoed
     * back instead. See docs/sync-protocol.md § Recipe Images.
     */
    val imageBlobId: String? = null
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

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SyncPushResponse(
    val accepted: List<AcceptedEntity>,
    val conflicts: List<ConflictEntity>,
    val errors: List<SyncError>,
    val serverTimestamp: Long,
    /** Reference entities required to resolve every [ConflictEntity.serverVersion] locally. */
    val referenceData: SyncReferenceData,
    @EncodeDefault val bookmarkedRecipes: List<BookmarkPushResult> = emptyList(),
    @EncodeDefault val bookmarkErrors: List<BookmarkPushError> = emptyList(),
    @EncodeDefault val mealPlans: MealPlanPushResults = MealPlanPushResults(),
    @EncodeDefault val groceryListItems: GroceryItemPushResults = GroceryItemPushResults()
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
    INVALID_LABEL("labelId is invalid"),
    INVALID_OWNER("ownerId is invalid"),
    OWNER_MISMATCH("ownerId does not match authenticated user for a new meal plan"),
    MEAL_PLAN_NOT_ACCESSIBLE("meal plan does not exist or caller cannot edit it"),
    INVALID_HOUSEHOLD("householdId does not correspond to caller's active household"),
    MEAL_PLAN_RECIPE_NOT_ACCESSIBLE("a referenced recipe does not exist or is not accessible to the caller")
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

@Serializable
data class SyncUser(
    val uuid: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String,
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

/**
 * Response for `GET /api/v1/recipes/{recipeId}` — an anonymous-capable single-recipe fetch
 * used to hydrate a search result the client hasn't synced yet (see ChefAI#186). Shares
 * [SyncRecipe]/[SyncReferenceData] with the pull/push-conflict responses so a client can
 * reuse its existing aggregate-upsert code path.
 *
 * [creators] is not part of [SyncReferenceData] (that type is also embedded in
 * [SyncPushResponse], reused as-is) but is required for the same FK-safety reason
 * [SyncPullResponse.creators] is: `recipes.creator_id` is itself a foreign key, and a fresh
 * anonymous client's local `users` table is typically empty.
 */
@Serializable
data class RecipeDetailResponse(
    val recipe: SyncRecipe,
    val referenceData: SyncReferenceData,
    val creators: List<SyncUser>
)

@Serializable
data class GenerateMealPlanStatelessRequest(val preferencesJson: String)

/**
 * Response for `POST /api/v1/meal-plans/generate` — the anonymous-capable stateless generator.
 * Shaped like a pull page rather than a bare day list: an anonymous device has typically never
 * received the assigned recipes, and a 7-day DINNER_AND_LUNCH plan would otherwise need 14
 * follow-up GET /api/v1/recipes/{id} calls. [creators] is separate from [referenceData] for the
 * same FK-safety reason as [RecipeDetailResponse.creators].
 */
@Serializable
data class GenerateMealPlanStatelessResponse(
    val days: List<SyncMealPlanDayDto>,
    val recipes: List<SyncRecipe>,
    val referenceData: SyncReferenceData,
    val creators: List<SyncUser>
)

@Serializable
data class SyncBookmark(
    val userId: String,
    val recipeId: String,
    val updatedAt: Long,
    val deletedAt: Long?
)

@Serializable
data class BookmarkPushResult(
    val userId: String,
    val recipeId: String,
    val syncState: String,
    val serverUpdatedAt: Long
)

@Serializable
data class BookmarkPushError(
    val recipeId: String,
    val reason: BookmarkErrors,
    val message: String
)

@Serializable
enum class BookmarkErrors(val message: String) {
    INVALID_RECIPE_ID("recipeId is not a valid UUID"),
    RECIPE_NOT_FOUND("recipe does not exist or is not accessible"),
    USER_MISMATCH("userId does not match authenticated user")
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SyncPullResponse(
    val recipes: List<SyncRecipe>,
    @EncodeDefault val creators: List<SyncUser> = emptyList(),
    val ingredients: List<SyncIngredient>,
    val allergens: List<SyncAllergen>,
    val sourceClassifications: List<SyncSourceClassification>,
    val tags: List<SyncTag>,
    val labels: List<SyncLabel>,
    @EncodeDefault val bookmarkedRecipes: List<SyncBookmark> = emptyList(),
    @EncodeDefault val mealPlans: List<SyncMealPlanDto> = emptyList(),
    @EncodeDefault val groceryListItems: List<SyncGroceryListItem> = emptyList(),
    val serverTimestamp: Long,
    val hasMore: Boolean
)

// ── Meal Plan DTOs ──────────────────────────────────────────────────────────

@Serializable
data class SyncMealPlanDto(
    val uuid: String,
    /**
     * The plan's real owner — never inferred from the caller. A household member who can edit a
     * shared plan is frequently not its owner; the client must not stamp `ownerId = <whoever
     * pulled it>`, or the first shared plan a member pulls silently reassigns ownership on their
     * device. On push the server persists this as-is after verifying the caller may write the
     * plan (owner, or an active member of `householdId`) — see docs/household-architecture.md.
     */
    val ownerId: String,
    /** Null = personal, unchanged pre-households behavior. Non-null = visible to every active
     *  member of that household, not just [ownerId]. */
    val householdId: String? = null,
    val name: String,
    val status: String,
    val preferencesJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val days: List<SyncMealPlanDayDto>
)

@Serializable
data class SyncMealPlanDayDto(
    val uuid: String,
    val dayIndex: Int,
    val dinnerRecipeId: String?,
    val lunchRecipeId: String?
)

@Serializable
data class MealPlanPushResult(
    val uuid: String,
    val serverUpdatedAt: Long
)

@Serializable
data class MealPlanPushResults(
    val accepted: List<MealPlanPushResult> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val errors: List<SyncError> = emptyList()
)

@Serializable
data class GenerateMealPlanResponse(
    val uuid: String,
    val status: String,
    val updatedAt: Long
)

// ── Grocery List DTOs ───────────────────────────────────────────────────────

/**
 * An explicit `checked: Boolean`, not a tombstone-on-uncheck: unchecking an item is an ordinary
 * LWW update, not a delete-then-recreate, so a toggle/untoggle cycle never accumulates tombstone
 * rows. [deletedAt] means only "this item left the list entirely" (e.g. removed from the plan).
 */
@Serializable
data class SyncGroceryListItem(
    val mealPlanId: String,
    /** Client-derived, opaque (e.g. a normalized ingredient name); validated non-blank, <= 256 chars. */
    val itemKey: String,
    val checked: Boolean,
    /** Whoever last checked/unchecked this item. Server-derived from the pushing caller, never
     *  trusted from the payload — see [com.tenmilelabs.domain.service.SyncService]. */
    val checkedBy: String?,
    /** Client logical clock; the LWW comparand against `serverUpdatedAtMillis`. */
    val updatedAt: Long,
    val deletedAt: Long?
)

@Serializable
data class GroceryItemPushResult(
    val mealPlanId: String,
    val itemKey: String,
    val serverUpdatedAt: Long
)

/** Identifies one grocery item by its compound key — used where a full item body isn't needed. */
@Serializable
data class GroceryItemIdentifier(
    val mealPlanId: String,
    val itemKey: String
)

@Serializable
data class GroceryItemPushError(
    val mealPlanId: String,
    val itemKey: String,
    val reason: GroceryItemErrors,
    val message: String
)

@Serializable
enum class GroceryItemErrors(val message: String) {
    INVALID_MEAL_PLAN_ID("mealPlanId is not a valid UUID"),
    MEAL_PLAN_NOT_ACCESSIBLE("meal plan does not exist or caller cannot edit it"),
    INVALID_ITEM_KEY("itemKey must be non-blank and at most 256 characters")
}

@Serializable
data class GroceryItemPushResults(
    val accepted: List<GroceryItemPushResult> = emptyList(),
    val conflicts: List<GroceryItemIdentifier> = emptyList(),
    val errors: List<GroceryItemPushError> = emptyList()
)
