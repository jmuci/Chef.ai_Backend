package com.tenmilelabs.domain.repository

import com.tenmilelabs.application.dto.SyncBookmark
import com.tenmilelabs.application.dto.SyncMealPlanDto
import com.tenmilelabs.application.dto.SyncMealPlanDayDto
import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.application.dto.SyncReferenceData
import com.tenmilelabs.application.dto.SyncUser
import kotlinx.datetime.Instant
import java.util.UUID

data class SyncRecipeRecord(
    val recipe: SyncRecipe,
    val serverUpdatedAtMillis: Long
)

data class SyncMealPlanRecord(
    val plan: SyncMealPlanDto,
    val serverUpdatedAtMillis: Long
)

interface SyncRepository {
    /**
     * Loads a single recipe aggregate by [uuid], including its active steps,
     * ingredients, tags, and labels. Returns null if no recipe with that UUID exists.
     */
    suspend fun getRecipe(uuid: UUID): SyncRecipeRecord?

    /**
     * Persists a full recipe aggregate using replace semantics for all child
     * collections (steps, ingredients, tags, labels). Existing children are
     * deleted and re-inserted from the incoming [recipe] so the server snapshot
     * is atomically consistent with the client payload.
     *
     * [serverUpdatedAt] is stamped onto every row to serve as the sync cursor.
     */
    suspend fun upsertRecipeAggregate(recipe: SyncRecipe, serverUpdatedAt: Instant)

    /**
     * Returns recipe aggregates whose [server_updated_at] is after [sinceMillis],
     * scoped to recipes owned by [userId] or marked PUBLIC.
     *
     * Results are ordered by [server_updated_at] ascending for deterministic
     * cursor-based paging. Pass [limit] + 1 at the call site to cheaply detect
     * whether a next page exists.
     */
    suspend fun findDeltaRecipes(
        userId: UUID,
        sinceMillis: Long,
        limit: Int
    ): List<SyncRecipeRecord>

    /**
     * Returns true if an ingredient with [uuid] exists in the catalogue,
     * regardless of soft-delete status. Used to validate push payloads before
     * persisting recipe aggregates.
     */
    suspend fun ingredientExists(uuid: UUID): Boolean

    /**
     * Collects all reference entities (ingredients, allergens, source classifications,
     * tags, labels) required for the client to satisfy FK constraints when persisting
     * a set of recipe aggregates.
     *
     * When [sinceMillis] is non-null each entity type is fetched as the union of:
     *  - entities with server_updated_at > sinceMillis (delta — client needs updates)
     *  - entities directly referenced by the recipes (gap — client may never have seen them)
     *
     * When [sinceMillis] is null only the directly referenced entities are fetched.
     * This mode is used for push-conflict payloads where no cursor is available.
     *
     * Allergen and sourceClassification IDs are derived from the *returned* ingredients
     * (not just from [ingredientIds]) so that delta ingredients always pull in their
     * own transitive dependencies.
     */
    suspend fun collectReferenceData(
        ingredientIds: Set<UUID>,
        tagIds: Set<UUID>,
        labelIds: Set<UUID>,
        sinceMillis: Long?
    ): SyncReferenceData

    /**
     * Collects user rows required for recipes in the current page.
     *
     * When [sinceMillis] is non-null, applies the same delta+gap union semantics
     * used by pull reference data:
     *  - users updated after sinceMillis (delta)
     *  - users referenced by [creatorIds] (gap)
     *
     * When [sinceMillis] is null, returns only [creatorIds].
     */
    suspend fun collectCreators(
        creatorIds: Set<UUID>,
        sinceMillis: Long?
    ): List<SyncUser>

    /**
     * Resolves [ids] against the tag catalogue and returns only those UUIDs
     * that exist. Used to silently drop unknown tag references on push rather
     * than failing the entire recipe aggregate.
     */
    suspend fun existingTagIds(ids: Set<UUID>): Set<UUID>

    /**
     * Resolves [ids] against the label catalogue and returns only those UUIDs
     * that exist. Used to silently drop unknown label references on push rather
     * than failing the entire recipe aggregate.
     */
    suspend fun existingLabelIds(ids: Set<UUID>): Set<UUID>

    /**
     * Returns true if [recipeId] exists and is accessible by [userId] —
     * i.e., the user is the creator or the recipe is PUBLIC.
     * Used to enforce the bookmark privacy rule before upsert.
     */
    suspend fun isRecipeAccessibleBy(userId: UUID, recipeId: UUID): Boolean

    /**
     * Upserts a bookmark row for the given (userId, recipeId) pair.
     * [deletedAt] non-null → soft-delete (tombstone); null → active bookmark.
     * [serverUpdatedAt] is the server-authoritative timestamp used as the sync cursor.
     */
    suspend fun upsertBookmark(
        userId: UUID,
        recipeId: UUID,
        deletedAt: Instant?,
        serverUpdatedAt: Instant
    )

    /**
     * Returns all bookmark rows for [userId] whose [server_updated_at] is after [sinceMillis].
     * Includes tombstones (deleted_at non-null) so the client can handle removals.
     */
    suspend fun findDeltaBookmarks(userId: UUID, sinceMillis: Long): List<SyncBookmark>

    // ── Meal Plans ────────────────────────────────────────────────────────────

    /**
     * Loads a single meal plan by [uuid] scoped to [userId].
     * Returns null if not found or owned by a different user.
     */
    suspend fun getMealPlanForUser(uuid: UUID, userId: UUID): SyncMealPlanRecord?

    /**
     * Upserts a meal plan using last-writer-wins semantics on [updated_at].
     * Replaces all [meal_plan_days] rows atomically (delete + re-insert).
     * When [plan.deletedAt] is non-null the plan is soft-deleted.
     */
    suspend fun upsertMealPlan(plan: SyncMealPlanDto, userId: UUID, serverUpdatedAt: Instant)

    /**
     * Returns meal plans for [userId] whose [server_updated_at] is after [sinceMillis].
     * Includes soft-deleted plans (deletedAt non-null) so the client can tombstone them.
     * Days are nested inside each returned plan.
     */
    suspend fun findDeltaMealPlans(userId: UUID, sinceMillis: Long): List<SyncMealPlanRecord>

    /**
     * Sets the [status] field and bumps [server_updated_at] on the given plan.
     * Used by the generation pipeline to transition DRAFT → GENERATING → READY.
     */
    suspend fun updateMealPlanStatus(planId: UUID, status: String, serverUpdatedAt: Instant)

    /**
     * Replaces all day rows for [planId] atomically.
     * Used by the generation pipeline after recipe assignment is complete.
     */
    suspend fun replaceMealPlanDays(planId: UUID, days: List<SyncMealPlanDayDto>)

    /**
     * Returns recipe UUIDs that are candidates for meal plan generation given
     * the provided filter criteria.
     *
     * [recipeSource] — "COLLECTION_ONLY" limits to bookmarked recipes; "INCLUDE_PUBLIC"
     * includes all recipes owned by [userId] or marked PUBLIC.
     *
     * [dietaryRestrictionTags] — tag display names (e.g. "VEGAN", "GLUTEN_FREE") that
     * a candidate recipe must ALL be tagged with. Empty list means no restriction.
     *
     * [maxPrepTimeMinutes] — upper bound on prep_time_minutes + cook_time_minutes.
     * Null means no time constraint.
     */
    suspend fun findCandidateRecipeIds(
        userId: UUID,
        recipeSource: String,
        dietaryRestrictionTags: List<String>,
        maxPrepTimeMinutes: Int?
    ): List<UUID>
}
