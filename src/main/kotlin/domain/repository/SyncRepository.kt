package com.tenmilelabs.domain.repository

import com.tenmilelabs.application.dto.SyncBookmark
import com.tenmilelabs.application.dto.SyncGroceryListItem
import com.tenmilelabs.application.dto.SyncMealPlanDto
import com.tenmilelabs.application.dto.SyncMealPlanDayDto
import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.application.dto.SyncReferenceData
import com.tenmilelabs.application.dto.SyncUser
import kotlinx.datetime.Instant
import java.util.UUID

/** Result of [SyncRepository.upsertRecipeAggregate]'s write-time re-validation. */
sealed interface RecipeUpsertOutcome {
    data object Applied : RecipeUpsertOutcome

    /**
     * The live row is newer than the payload — either it changed after the caller's own
     * (separately transacted) pre-check, or a concurrent push inserted the same brand-new uuid
     * first. Nothing was written.
     */
    data object ServerNewer : RecipeUpsertOutcome

    /** A step uuid in the payload already belongs to a different recipe. Nothing was written. */
    data object StepIdTaken : RecipeUpsertOutcome
}

data class SyncRecipeRecord(
    val recipe: SyncRecipe,
    val serverUpdatedAtMillis: Long
)

data class SyncMealPlanRecord(
    val plan: SyncMealPlanDto,
    val serverUpdatedAtMillis: Long
)

data class SyncGroceryItemRecord(
    val item: SyncGroceryListItem,
    val serverUpdatedAtMillis: Long
)

interface SyncRepository {
    /**
     * Loads a single recipe aggregate by [uuid], including its active steps,
     * ingredients, tags, and labels. Returns null if no recipe with that UUID exists.
     */
    suspend fun getRecipe(uuid: UUID): SyncRecipeRecord?

    /**
     * Batched counterpart to [getRecipe] — loads every recipe in [uuids] (each including its
     * active steps, ingredients, tags, and labels) in one round trip instead of one call per id.
     * Used to hydrate [findHouseholdVisibleRecipeIds]'s gap-clause recipes in
     * [com.tenmilelabs.domain.service.SyncService.pullRecipes]. An id with no matching recipe is
     * silently omitted, same as [getRecipe] returning null for it would be.
     */
    suspend fun getRecipes(uuids: Set<UUID>): List<SyncRecipeRecord>

    /**
     * Persists a full recipe aggregate using replace semantics for all child
     * collections (steps, ingredients, tags, labels). Existing children are
     * deleted and re-inserted from the incoming [recipe] so the server snapshot
     * is atomically consistent with the client payload.
     *
     * [serverUpdatedAt] is stamped onto every row to serve as the sync cursor.
     *
     * Locks the existing row and re-runs the staleness check against it before writing, the same
     * way [upsertMealPlan] does, so two concurrent pushes of one recipe can't both be accepted with
     * the later silently overwriting the earlier.
     */
    suspend fun upsertRecipeAggregate(recipe: SyncRecipe, serverUpdatedAt: Instant): RecipeUpsertOutcome

    /**
     * Returns recipe aggregates whose [server_updated_at] is after [sinceMillis],
     * scoped to recipes owned by [userId] or marked PUBLIC.
     *
     * Results are ordered by [server_updated_at] ascending for deterministic
     * cursor-based paging. Pass [limit] + 1 at the call site to cheaply detect
     * whether a next page exists.
     *
     * Deliberately does **not** also widen for household-shared recipes (see
     * [findHouseholdVisibleRecipeIds]) — that set is unconditional on `server_updated_at` (a
     * true gap, like [collectReferenceData]'s helpers), and blending an unconditional set into
     * this method's own `ORDER BY server_updated_at` pagination would let old gap rows drag the
     * `/sync/pull` cursor backwards. [com.tenmilelabs.domain.service.SyncService.pullRecipes]
     * merges the two as separate steps instead, exactly as it already does for `creators`.
     */
    suspend fun findDeltaRecipes(
        userId: UUID,
        sinceMillis: Long,
        limit: Int
    ): List<SyncRecipeRecord>

    /**
     * Recipe UUIDs visible to [userId] purely through household sharing: referenced (as a dinner
     * or lunch pick) by a day in a non-deleted meal plan under the caller's active household —
     * regardless of the recipe's own `privacy` or who created it. A household member shares a
     * recipe simply by using it in a shared plan; this read path trusts that every such reference
     * was already validated accessible-to-its-pusher at push time (see [upsertMealPlan]'s KDoc),
     * so it applies no further filtering itself. Empty if [userId] has no active household.
     */
    suspend fun findHouseholdVisibleRecipeIds(userId: UUID): Set<UUID>

    /**
     * True if [recipeId] is visible to [userId] specifically via [findHouseholdVisibleRecipeIds]
     * — i.e. neither owned by [userId] nor `PUBLIC`, but reachable through a shared meal plan.
     * Used by [com.tenmilelabs.domain.service.SyncService.getRecipeDetail] to extend the
     * single-recipe-fetch visibility rule the same way the pull-side gap clause does.
     */
    suspend fun isRecipeHouseholdVisible(userId: UUID, recipeId: UUID): Boolean

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
     * Collects the user rows for exactly [creatorIds] — the creators/owners the current response
     * references — and nothing else.
     *
     * Deliberately gap-only, unlike [collectReferenceData]'s delta+gap union. Reference entities
     * (tags, ingredients, ...) are a shared catalog, so sending every changed one is harmless; users
     * are not. A delta clause here (`updated_at > since`) sent every account in the table — email
     * included — to any caller pulling with `since=0`.
     */
    suspend fun collectCreators(creatorIds: Set<UUID>): List<SyncUser>

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
     * Batched counterpart to [isRecipeAccessibleBy] that also allows [findHouseholdVisibleRecipeIds]
     * — i.e. the subset of [recipeIds] that [userId] may reference in a meal plan: owned by them,
     * `PUBLIC`, or visible only through their household's recipe gap clause (a co-member's private
     * recipe already referenced by a shared plan). One query for the whole set instead of one call
     * per id, and — unlike [isRecipeAccessibleBy] — accounts for household visibility, which a
     * shared plan's day references must be checked against too.
     */
    suspend fun accessibleRecipeIds(userId: UUID, recipeIds: Set<UUID>): Set<UUID>

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
     * True if [userId] is currently an ACTIVE member of [householdId]. Used only to validate a
     * brand-new shared plan's claimed `householdId` at push time — an unvalidated claim would let
     * a push plant a plan (and, via the recipe gap clause, leak referenced recipes) into a
     * household the pusher doesn't actually belong to.
     */
    suspend fun isActiveHouseholdMember(userId: UUID, householdId: UUID): Boolean

    /**
     * Loads a single meal plan by [uuid], visible to [userId] if they own it OR are an active
     * member of the household it's shared with (`household_id`). Returns null if not found, or
     * found but not accessible to [userId] — same "don't leak existence" posture as
     * [isRecipeAccessibleBy]. This is the sole authorization choke point for meal-plan writes:
     * [com.tenmilelabs.domain.service.SyncService] must reject a push this returns null for
     * (after using [mealPlanExists] to tell "doesn't exist yet" apart from "exists but forbidden")
     * rather than letting [upsertMealPlan] silently overwrite a plan the caller can't edit.
     */
    suspend fun getMealPlanForMember(uuid: UUID, userId: UUID): SyncMealPlanRecord?

    /**
     * True if a meal plan with [uuid] exists at all, regardless of ownership/membership. Exists
     * only to disambiguate [getMealPlanForMember] returning null: "doesn't exist" (a legitimate
     * new plan — proceed to insert) vs. "exists but the caller can't write it" (reject).
     */
    suspend fun mealPlanExists(uuid: UUID): Boolean

    /**
     * Upserts a meal plan using last-writer-wins semantics on [updated_at]. Replaces all
     * [meal_plan_days] rows atomically (delete + re-insert). When [plan.deletedAt] is non-null the
     * plan is soft-deleted.
     *
     * [plan.ownerId] and [plan.householdId] are persisted as-is on insert; neither is ever
     * touched on update (a plan's owner and household assignment change only through
     * [com.tenmilelabs.domain.service.HouseholdService], never through sync). The caller
     * ([com.tenmilelabs.domain.service.SyncService]) must have already verified via
     * [getMealPlanForMember] that the pushing user may write this plan, and — for a brand-new
     * plan — that [plan.ownerId] names the caller and [plan.householdId] (if any) names their own
     * active household. This method trusts that verification; it performs none itself.
     *
     * The last-writer-wins check is re-validated here against the live row, atomically with the
     * write (implementations lock the row first) — not just trusted from a caller's earlier,
     * separately-transacted read. Two concurrent pushes for the same plan can otherwise both
     * observe "no conflict" before either commits, and the second would silently overwrite the
     * first with no conflict ever reported. Returns `true` if the write was applied, `false` if a
     * newer row was found (or a concurrent insert for a brand-new plan won the race) and nothing
     * was written — the caller must report this the same as its own pre-check conflict.
     */
    suspend fun upsertMealPlan(plan: SyncMealPlanDto, serverUpdatedAt: Instant): Boolean

    /**
     * Returns meal plans visible to [userId] whose [server_updated_at] is after [sinceMillis]:
     * their own plans, plus plans shared with their current active household. Includes
     * soft-deleted plans (deletedAt non-null) so the client can tombstone them.
     *
     * Also includes **removal tombstones**: if [userId] was removed from a household after
     * [sinceMillis] (`household_members.status = 'REMOVED' AND server_removed_at > sinceMillis`),
     * every plan still under that household's `household_id` is returned with `deletedAt`
     * synthesized to that removal's `server_removed_at` — for this caller only. The underlying
     * row's real `deleted_at` stays null for everyone still in the household; this is purely a
     * per-caller signal to drop plans they no longer have access to. See
     * docs/household-architecture.md.
     *
     * Not paginated — same as [findDeltaBookmarks], this returns its full matching set every
     * call and plays no part in `/sync/pull`'s cursor, which [findDeltaRecipes] alone drives.
     */
    suspend fun findDeltaMealPlans(userId: UUID, sinceMillis: Long): List<SyncMealPlanRecord>

    // ── Grocery List ──────────────────────────────────────────────────────────

    /** Loads one grocery item by its compound key. Unscoped — the caller must have already
     *  verified access to [mealPlanId] via [getMealPlanForMember] before calling this. */
    suspend fun getGroceryListItem(mealPlanId: UUID, itemKey: String): SyncGroceryItemRecord?

    /**
     * Upserts a grocery item using last-writer-wins semantics on [SyncGroceryListItem.updatedAt].
     * Trusts [item] completely — the caller ([com.tenmilelabs.domain.service.SyncService]) must
     * have already validated `mealPlanId`/`itemKey` and the authorization choke point
     * ([getMealPlanForMember]), same division of labor as [upsertMealPlan].
     */
    suspend fun upsertGroceryListItem(item: SyncGroceryListItem, serverUpdatedAt: Instant)

    /**
     * Returns grocery items visible to [userId] — under a plan they own or their active household
     * shares — whose `server_updated_at` is after [sinceMillis]. Includes the same **removal
     * tombstones** as [findDeltaMealPlans]: every item under a plan that belonged to a household
     * [userId] was removed from after [sinceMillis] is returned with `deletedAt` synthesized to
     * that removal's `server_removed_at`. Not paginated, same as [findDeltaMealPlans].
     */
    suspend fun findDeltaGroceryListItems(userId: UUID, sinceMillis: Long): List<SyncGroceryItemRecord>

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
     * A null [userId] means an anonymous caller: candidates are scoped to
     * `privacy = 'PUBLIC' AND deleted_at IS NULL`, and "COLLECTION_ONLY" is not meaningful for a
     * null user — it is treated as "INCLUDE_PUBLIC".
     *
     * [recipeSource] — "COLLECTION_ONLY" limits to recipes bookmarked by [userId] or authored
     * by [userId] (so a user's own recipes are always usable, even unbookmarked); "INCLUDE_PUBLIC"
     * includes all recipes owned by [userId] or marked PUBLIC. A bookmark only counts if the
     * bookmarked recipe is still accessible to [userId] (not soft-deleted, and either owned by
     * [userId] or still `privacy = PUBLIC`) — the same predicate `/sync/pull` uses, so a
     * candidate is never returned that the client's own pull could never deliver.
     *
     * [dietaryRestrictionTags] — tag display names (e.g. "VEGAN", "GLUTEN_FREE") that
     * a candidate recipe must ALL be tagged with. Empty list means no restriction.
     *
     * [maxPrepTimeMinutes] — upper bound on prep_time_minutes + cook_time_minutes.
     * Null means no time constraint.
     */
    suspend fun findCandidateRecipeIds(
        userId: UUID?,
        recipeSource: String,
        dietaryRestrictionTags: List<String>,
        maxPrepTimeMinutes: Int?
    ): List<UUID>

    /**
     * Batched ranking metadata for [recipeIds], used by [assignRecipesToDays][com.tenmilelabs.domain.service.MealPlanGenerationService]
     * to bias day assignment. [RecipeRankingMetadata.dominantCategory] is the mode (most frequent,
     * ties broken alphabetically) of [SourceClassificationTable.category] across a recipe's
     * classified ingredients — null when none of its ingredients are classified.
     */
    suspend fun findRecipeRankingMetadata(recipeIds: Set<UUID>): Map<UUID, RecipeRankingMetadata>

    /**
     * Recipe UUIDs (dinner or lunch) assigned across [userId]'s last [limit] `READY` meal plans,
     * excluding [excludePlanId] (the plan currently being generated) and soft-deleted plans.
     * Used to softly deprioritize — never hard-exclude — recently-served recipes in a new plan.
     */
    suspend fun findRecentlyUsedRecipeIds(userId: UUID, excludePlanId: UUID, limit: Int): Set<UUID>
}

data class RecipeRankingMetadata(
    val servings: Int,
    val dominantCategory: String?
)
