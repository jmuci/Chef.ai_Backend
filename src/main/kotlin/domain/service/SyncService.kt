package com.tenmilelabs.domain.service

import com.tenmilelabs.application.dto.AcceptedEntity
import com.tenmilelabs.application.dto.BookmarkErrors
import com.tenmilelabs.application.dto.BookmarkPushError
import com.tenmilelabs.application.dto.BookmarkPushResult
import com.tenmilelabs.application.dto.ConflictEntity
import com.tenmilelabs.application.dto.ConflictReasons
import com.tenmilelabs.application.dto.GroceryItemErrors
import com.tenmilelabs.application.dto.GroceryItemIdentifier
import com.tenmilelabs.application.dto.GroceryItemPushError
import com.tenmilelabs.application.dto.GroceryItemPushResult
import com.tenmilelabs.application.dto.GroceryItemPushResults
import com.tenmilelabs.application.dto.MealPlanPushResult
import com.tenmilelabs.application.dto.MealPlanPushResults
import com.tenmilelabs.application.dto.SyncError
import com.tenmilelabs.application.dto.SyncErrors
import com.tenmilelabs.application.dto.SyncGroceryListItem
import com.tenmilelabs.application.dto.SyncMealPlanDto
import com.tenmilelabs.application.dto.SyncPullResponse
import com.tenmilelabs.application.dto.SyncPushRequest
import com.tenmilelabs.application.dto.SyncPushResponse
import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.application.dto.SyncReferenceData
import com.tenmilelabs.application.dto.SyncUser
import com.tenmilelabs.domain.repository.SyncRecipeRecord
import com.tenmilelabs.domain.repository.SyncRepository
import com.tenmilelabs.domain.repository.UserPreferencesRepository
import com.tenmilelabs.domain.util.millisecondPrecisionNow
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

sealed interface RecipeDetailResult {
    data class Found(
        val recipe: SyncRecipe,
        val referenceData: SyncReferenceData,
        val creators: List<SyncUser>
    ) : RecipeDetailResult
    data object NotFound : RecipeDetailResult
}

/** Merged result of [SyncService.getRecipeDetails] — the aggregate form of several [RecipeDetailResult.Found]. */
data class RecipeDetailsBundle(
    val recipes: List<SyncRecipe>,
    val referenceData: SyncReferenceData,
    val creators: List<SyncUser>
)

class SyncService(
    private val syncRepository: SyncRepository,
    private val log: Logger,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    /**
     * Safety cap on how far [fetchStablePage] will widen a fetch to find the end of a
     * `server_updated_at` tie that starts at the pull's own `since` cursor.
     */
    companion object {
        private const val TIE_ESCAPE_FETCH_LIMIT = 5_000

        /**
         * Upper bound on `/sync/pull`'s `limit`. Mirrors what [RecipeSearchService] does with its
         * own params: the repository trusts its callers, so the clamp lives here rather than in
         * the route. Without it a caller could ask for `Int.MAX_VALUE` rows — and worse, the
         * `limit + 1` in [fetchStablePage] would overflow to `Int.MIN_VALUE` and reach Exposed's
         * `.limit()` as a negative.
         */
        const val MAX_PULL_LIMIT = 500
    }

    /**
     * Processes client dirty recipe aggregates for authenticated user.
     *
     * Behavior:
     * - Accepts insert/update when server is not newer.
     * - Returns conflict when existing `serverUpdatedAt` is newer than client `updatedAt`.
     * - Returns per-entity validation errors without failing the whole batch.
     */
    suspend fun pushRecipes(userId: UUID, request: SyncPushRequest): SyncPushResponse {
        val accepted = mutableListOf<AcceptedEntity>()
        val conflicts = mutableListOf<ConflictEntity>()
        val errors = mutableListOf<SyncError>()

        request.recipes.forEach { recipe ->
            val recipeUuid = parseUuid(recipe.uuid) {
                errors += SyncError(recipe.uuid, SyncErrors.INVALID_UUID, SyncErrors.INVALID_UUID.message)
            } ?: return@forEach

            val creatorId = parseUuid(recipe.creatorId) {
                errors += SyncError(recipe.uuid, SyncErrors.INVALID_CREATOR, SyncErrors.INVALID_CREATOR.message)
            } ?: return@forEach

            if (creatorId != userId) {
                errors += SyncError(
                    uuid = recipe.uuid,
                    reason = SyncErrors.CREATOR_MISMATCH,
                    message = SyncErrors.CREATOR_MISMATCH.message
                )
                return@forEach
            }

            if (recipe.privacy != "PUBLIC" && recipe.privacy != "PRIVATE") {
                errors += SyncError(recipe.uuid, SyncErrors.INVALID_PRIVACY, SyncErrors.INVALID_PRIVACY.message)
                return@forEach
            }

            if (!validateIngredients(recipe, errors)) {
                return@forEach
            }

            if (!validateTagAndLabelIds(recipe, errors)) {
                return@forEach
            }

            val existing = syncRepository.getRecipe(recipeUuid)

            // The CREATOR_MISMATCH check above only proves the *payload* names the caller as
            // creator. It says nothing about who owns the row already sitting at this uuid, and
            // `getRecipe` is deliberately unscoped (it backs the anonymous-capable detail fetch
            // too). Without this second check a caller could address any recipe in the table by
            // uuid: the staleness check below is not a permission check, and `updatedAt` is
            // client-supplied, so a far-future value walks straight past it into an upsert that
            // overwrites the row and reassigns its creator. Rejecting before the conflict branch
            // also stops the conflict response from echoing another user's recipe back as
            // `serverVersion`.
            if (existing != null && existing.recipe.creatorId != userId.toString()) {
                errors += SyncError(
                    uuid = recipe.uuid,
                    reason = SyncErrors.CREATOR_MISMATCH,
                    message = SyncErrors.CREATOR_MISMATCH.message
                )
                return@forEach
            }

            if (existing != null && existing.serverUpdatedAtMillis > recipe.updatedAt) {
                conflicts += ConflictEntity(
                    uuid = recipe.uuid,
                    reason = ConflictReasons.SERVER_NEWER,
                    serverVersion = existing.recipe
                )
                return@forEach
            }

            val now = millisecondPrecisionNow()
            syncRepository.upsertRecipeAggregate(recipe, now)
            accepted += AcceptedEntity(
                uuid = recipe.uuid,
                serverUpdatedAt = now.toEpochMilliseconds()
            )
        }

        val conflictReferenceData = if (conflicts.isEmpty()) {
            SyncReferenceData()
        } else {
            val ingredientIds = conflicts
                .flatMap { it.serverVersion.ingredients }
                .map { UUID.fromString(it.ingredientId) }
                .toSet()
            val tagIds = conflicts
                .flatMap { it.serverVersion.tagIds }
                .map { UUID.fromString(it) }
                .toSet()
            val labelIds = conflicts
                .flatMap { it.serverVersion.labelIds }
                .map { UUID.fromString(it) }
                .toSet()
            syncRepository.collectReferenceData(
                sinceMillis = null,
                ingredientIds = ingredientIds,
                tagIds = tagIds,
                labelIds = labelIds
            )
        }

        log.info(
            "Sync push processed for user $userId: accepted=${accepted.size}, conflicts=${conflicts.size}, errors=${errors.size}"
        )
        // TODO Remove before release, or put behind debug flag.
        for (acceptedRecipe in accepted) {
            log.info(
                "Sync push accepted for user $userId, recipeId=${acceptedRecipe.uuid}, " +
                    "serverUpdatedAt=${acceptedRecipe.serverUpdatedAt}"
            )
        }

        for (conflict in conflicts) {
            log.info(
                "Sync push conflicted for user $userId, recipeId=${conflict.uuid}, " +
                    "reason=${conflict.reason}, clientUpdatedAt=${recipeClientUpdatedAt(conflict.uuid, request)}, " +
                    "serverUpdatedAt=${conflict.serverVersion.updatedAt}"
            )
        }

        for (error in errors) {
            log.info(
                "Sync push errored for user $userId, recipeId=${error.uuid}, " +
                    "reason=${error.reason}, message=${error.message}"
            )
        }

        val (bookmarkResults, bookmarkErrors) = processBookmarks(userId, request)
        val mealPlanResults = processMealPlans(userId, request.mealPlans)
        val groceryResults = processGroceryListItems(userId, request.groceryListItems)

        return SyncPushResponse(
            accepted = accepted,
            conflicts = conflicts,
            errors = errors,
            serverTimestamp = Clock.System.now().toEpochMilliseconds(),
            referenceData = conflictReferenceData,
            bookmarkedRecipes = bookmarkResults,
            bookmarkErrors = bookmarkErrors,
            mealPlans = mealPlanResults,
            groceryListItems = groceryResults
        )
    }

    private suspend fun processMealPlans(
        userId: UUID,
        mealPlans: List<SyncMealPlanDto>
    ): MealPlanPushResults {
        if (mealPlans.isEmpty()) return MealPlanPushResults()

        val accepted = mutableListOf<MealPlanPushResult>()
        val conflicts = mutableListOf<String>()
        val errors = mutableListOf<SyncError>()

        mealPlans.forEach { plan ->
            val planUuid = parseUuid(plan.uuid) {
                errors += SyncError(plan.uuid, SyncErrors.INVALID_UUID, SyncErrors.INVALID_UUID.message)
            } ?: return@forEach

            val ownerId = parseUuid(plan.ownerId) {
                errors += SyncError(plan.uuid, SyncErrors.INVALID_OWNER, SyncErrors.INVALID_OWNER.message)
            } ?: return@forEach

            var householdParseFailed = false
            val claimedHouseholdId = plan.householdId?.let {
                parseUuid(it) {
                    householdParseFailed = true
                    errors += SyncError(plan.uuid, SyncErrors.INVALID_HOUSEHOLD, SyncErrors.INVALID_HOUSEHOLD.message)
                }
            }
            if (householdParseFailed) return@forEach

            val existing = syncRepository.getMealPlanForMember(planUuid, userId)

            // getMealPlanForMember found nothing two different reasons: brand new, or it exists
            // but userId isn't the owner and isn't an active member of its household. Only the
            // first is a legitimate insert — the second must reject, or upsertMealPlan would
            // silently let an unrelated caller overwrite someone else's plan by guessing its uuid.
            if (existing == null && syncRepository.mealPlanExists(planUuid)) {
                errors += SyncError(
                    plan.uuid,
                    SyncErrors.MEAL_PLAN_NOT_ACCESSIBLE,
                    SyncErrors.MEAL_PLAN_NOT_ACCESSIBLE.message
                )
                return@forEach
            }

            // A brand-new plan's ownerId must name the caller, and a claimed householdId must be
            // the caller's own active household — both unvalidated would let a push plant content
            // (and, via the recipe gap clause below, leak a referenced private recipe) into
            // another user's identity or a household the pusher doesn't belong to.
            if (existing == null) {
                if (ownerId != userId) {
                    errors += SyncError(plan.uuid, SyncErrors.OWNER_MISMATCH, SyncErrors.OWNER_MISMATCH.message)
                    return@forEach
                }
                if (claimedHouseholdId != null && !syncRepository.isActiveHouseholdMember(userId, claimedHouseholdId)) {
                    errors += SyncError(plan.uuid, SyncErrors.INVALID_HOUSEHOLD, SyncErrors.INVALID_HOUSEHOLD.message)
                    return@forEach
                }
            }

            // Effective household after this push: unchanged on update (upsertMealPlan never
            // touches it), the validated claim on insert. Gates the recipe-reference check below —
            // a personal plan's day references are unrestricted, same as before households existed.
            val effectiveHouseholdId = existing?.plan?.householdId?.let { UUID.fromString(it) } ?: claimedHouseholdId
            if (effectiveHouseholdId != null) {
                val allReferencesAccessible = plan.days
                    .flatMap { listOfNotNull(it.dinnerRecipeId, it.lunchRecipeId) }
                    .all { recipeIdString ->
                        val recipeId = try {
                            UUID.fromString(recipeIdString)
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                        recipeId != null && syncRepository.isRecipeAccessibleBy(userId, recipeId)
                    }
                if (!allReferencesAccessible) {
                    errors += SyncError(
                        plan.uuid,
                        SyncErrors.MEAL_PLAN_RECIPE_NOT_ACCESSIBLE,
                        SyncErrors.MEAL_PLAN_RECIPE_NOT_ACCESSIBLE.message
                    )
                    return@forEach
                }
            }

            if (existing != null && existing.serverUpdatedAtMillis > plan.updatedAt) {
                conflicts += plan.uuid
                log.info("Meal plan push conflict for user $userId, planId=${plan.uuid}: server is newer")
                return@forEach
            }

            val now = millisecondPrecisionNow()
            syncRepository.upsertMealPlan(plan, now)
            userPreferencesRepository.upsertUserPreferences(userId, plan.preferencesJson, now)
            accepted += MealPlanPushResult(uuid = plan.uuid, serverUpdatedAt = now.toEpochMilliseconds())
            log.info("Meal plan push accepted for user $userId, planId=${plan.uuid}")
        }

        return MealPlanPushResults(accepted = accepted, conflicts = conflicts, errors = errors)
    }

    /**
     * Mirrors [processMealPlans]'s per-item shape (backend prompt §6.4). The authorization choke
     * point is [SyncRepository.getMealPlanForMember] — without it, any authenticated caller could
     * write checks against an arbitrary meal plan id.
     */
    private suspend fun processGroceryListItems(
        userId: UUID,
        items: List<SyncGroceryListItem>
    ): GroceryItemPushResults {
        if (items.isEmpty()) return GroceryItemPushResults()

        val accepted = mutableListOf<GroceryItemPushResult>()
        val conflicts = mutableListOf<GroceryItemIdentifier>()
        val errors = mutableListOf<GroceryItemPushError>()

        items.forEach { item ->
            val mealPlanId = try {
                UUID.fromString(item.mealPlanId)
            } catch (_: IllegalArgumentException) {
                errors += GroceryItemPushError(
                    item.mealPlanId,
                    item.itemKey,
                    GroceryItemErrors.INVALID_MEAL_PLAN_ID,
                    GroceryItemErrors.INVALID_MEAL_PLAN_ID.message
                )
                return@forEach
            }

            if (item.itemKey.isBlank() || item.itemKey.length > 256) {
                errors += GroceryItemPushError(
                    item.mealPlanId,
                    item.itemKey,
                    GroceryItemErrors.INVALID_ITEM_KEY,
                    GroceryItemErrors.INVALID_ITEM_KEY.message
                )
                return@forEach
            }

            if (syncRepository.getMealPlanForMember(mealPlanId, userId) == null) {
                errors += GroceryItemPushError(
                    item.mealPlanId,
                    item.itemKey,
                    GroceryItemErrors.MEAL_PLAN_NOT_ACCESSIBLE,
                    GroceryItemErrors.MEAL_PLAN_NOT_ACCESSIBLE.message
                )
                return@forEach
            }

            val existing = syncRepository.getGroceryListItem(mealPlanId, item.itemKey)
            if (existing != null && existing.serverUpdatedAtMillis > item.updatedAt) {
                conflicts += GroceryItemIdentifier(item.mealPlanId, item.itemKey)
                log.info(
                    "Grocery item push conflict for user $userId, mealPlanId=${item.mealPlanId}, " +
                        "itemKey=${item.itemKey}: server is newer"
                )
                return@forEach
            }

            val now = millisecondPrecisionNow()
            // checkedBy reflects who actually performed the toggle - the pushing caller - never
            // trusted from the payload; null when unchecking (nobody currently has it checked).
            val effectiveItem = item.copy(checkedBy = if (item.checked) userId.toString() else null)
            syncRepository.upsertGroceryListItem(effectiveItem, now)
            accepted += GroceryItemPushResult(item.mealPlanId, item.itemKey, now.toEpochMilliseconds())
        }

        return GroceryItemPushResults(accepted = accepted, conflicts = conflicts, errors = errors)
    }

    private suspend fun processBookmarks(
        userId: UUID,
        request: SyncPushRequest
    ): Pair<List<BookmarkPushResult>, List<BookmarkPushError>> {
        log.info("Sync push bookmark batch for user $userId: received=${request.bookmarkedRecipes.size}")
        if (request.bookmarkedRecipes.isEmpty()) return emptyList<BookmarkPushResult>() to emptyList()

        val results = mutableListOf<BookmarkPushResult>()
        val errors = mutableListOf<BookmarkPushError>()

        request.bookmarkedRecipes.forEach { bookmark ->
            val bookmarkUserId = parseUuid(bookmark.userId) {
                // If userId is malformed treat it as a mismatch — skip silently
            }
            if (bookmarkUserId == null || bookmarkUserId != userId) {
                log.warn(
                    "Bookmark USER_MISMATCH for recipeId=${bookmark.recipeId}: " +
                        "auth userId=$userId, bookmark.userId=${bookmark.userId}"
                )
                errors += BookmarkPushError(
                    recipeId = bookmark.recipeId,
                    reason = BookmarkErrors.USER_MISMATCH,
                    message = BookmarkErrors.USER_MISMATCH.message
                )
                return@forEach
            }

            val recipeId = parseUuid(bookmark.recipeId) {
                log.warn("Bookmark INVALID_RECIPE_ID: recipeId=${bookmark.recipeId} is not a valid UUID")
                errors += BookmarkPushError(
                    recipeId = bookmark.recipeId,
                    reason = BookmarkErrors.INVALID_RECIPE_ID,
                    message = BookmarkErrors.INVALID_RECIPE_ID.message
                )
            } ?: return@forEach

            if (!syncRepository.isRecipeAccessibleBy(userId, recipeId)) {
                log.warn(
                    "Bookmark RECIPE_NOT_FOUND for recipeId=$recipeId, userId=$userId: " +
                        "recipe does not exist or is private and owned by another user"
                )
                errors += BookmarkPushError(
                    recipeId = bookmark.recipeId,
                    reason = BookmarkErrors.RECIPE_NOT_FOUND,
                    message = BookmarkErrors.RECIPE_NOT_FOUND.message
                )
                return@forEach
            }

            val now = millisecondPrecisionNow()
            val deletedAt = bookmark.deletedAt?.let { Instant.fromEpochMilliseconds(it) }
            syncRepository.upsertBookmark(userId, recipeId, deletedAt, now)
            log.info(
                "Bookmark upserted for userId=$userId, recipeId=$recipeId, " +
                    "deletedAt=$deletedAt, serverUpdatedAt=$now"
            )
            results += BookmarkPushResult(
                userId = bookmark.userId,
                recipeId = bookmark.recipeId,
                syncState = "SYNCED",
                serverUpdatedAt = now.toEpochMilliseconds()
            )
        }

        return results to errors
    }

    /**
     * Returns delta recipe aggregates since client checkpoint.
     *
     * Uses `limit + 1` internally to compute `hasMore`, then returns
     * a page cursor (`serverTimestamp`) based on the last included item.
     */
    suspend fun pullRecipes(userId: UUID, sinceMillis: Long, limit: Int): SyncPullResponse {
        require(sinceMillis >= 0) { "since must be non-negative" }
        require(limit > 0) { "limit must be greater than 0" }

        val effectiveLimit = limit.coerceAtMost(MAX_PULL_LIMIT)
        val (page, hasMore) = fetchStablePage(userId, sinceMillis, effectiveLimit)
        // The cursor is derived from the delta page alone, before the household gap merge below —
        // see findDeltaRecipes's KDoc for why blending an unconditional-on-timestamp gap set into
        // this computation would risk dragging the cursor backwards.
        val cursor = page.lastOrNull()?.serverUpdatedAtMillis ?: sinceMillis

        // Household gap clause (§6.3): recipes visible only because a shared plan's day
        // references them, regardless of the recipe's own privacy/creator. Excludes anything
        // already in `page` and never affects `hasMore`/`cursor` above.
        val pageRecipeIds = page.map { UUID.fromString(it.recipe.uuid) }.toSet()
        val gapRecipes = (syncRepository.findHouseholdVisibleRecipeIds(userId) - pageRecipeIds)
            .mapNotNull { syncRepository.getRecipe(it) }
        val allRecipes = page + gapRecipes

        val ingredientIds = allRecipes
            .flatMap { it.recipe.ingredients }
            .map { UUID.fromString(it.ingredientId) }
            .toSet()
        val tagIds = allRecipes
            .flatMap { it.recipe.tagIds }
            .map { UUID.fromString(it) }
            .toSet()
        val labelIds = allRecipes
            .flatMap { it.recipe.labelIds }
            .map { UUID.fromString(it) }
            .toSet()
        val refData = syncRepository.collectReferenceData(
            sinceMillis = sinceMillis,
            ingredientIds = ingredientIds,
            tagIds = tagIds,
            labelIds = labelIds
        )

        val bookmarks = syncRepository.findDeltaBookmarks(userId, sinceMillis)
        val mealPlans = syncRepository.findDeltaMealPlans(userId, sinceMillis)
        val groceryItems = syncRepository.findDeltaGroceryListItems(userId, sinceMillis)

        // Union of recipe creators and meal-plan owners (backend prompt §0.2): the client upserts
        // `creators` before meal plans to satisfy a local FK on the owner id, so a household
        // member's shared plan whose owner authored none of this page's recipes would otherwise be
        // missing a row it needs.
        val creatorIds = allRecipes.map { UUID.fromString(it.recipe.creatorId) }.toSet() +
            mealPlans.map { UUID.fromString(it.plan.ownerId) }.toSet()
        val creators = syncRepository.collectCreators(
            creatorIds = creatorIds,
            sinceMillis = sinceMillis
        )

        log.info(
            "Sync pull for user $userId: recipes=${page.size}, gapRecipes=${gapRecipes.size}, hasMore=$hasMore, " +
                "ingredients=${refData.ingredients.size}, allergens=${refData.allergens.size}, " +
                "sourceClassifications=${refData.sourceClassifications.size}, " +
                "tags=${refData.tags.size}, labels=${refData.labels.size}, " +
                "bookmarks=${bookmarks.size}, mealPlans=${mealPlans.size}"
        )

        return SyncPullResponse(
            recipes = allRecipes.map { it.recipe },
            creators = creators,
            ingredients = refData.ingredients,
            allergens = refData.allergens,
            sourceClassifications = refData.sourceClassifications,
            tags = refData.tags,
            labels = refData.labels,
            bookmarkedRecipes = bookmarks,
            mealPlans = mealPlans.map { it.plan },
            groceryListItems = groceryItems.map { it.item },
            serverTimestamp = cursor,
            hasMore = hasMore
        )
    }

    /**
     * Fetches a single recipe aggregate for `GET /api/v1/recipes/{recipeId}` — the
     * anonymous-capable counterpart to [pullRecipes] used to hydrate a search result the
     * client hasn't synced yet (ChefAI#186). [userId] is nullable for the same reason
     * [RecipeSearchService.search]'s is: a null caller is anonymous, not an error.
     *
     * A recipe is visible if it's `PUBLIC`, `PRIVATE` and owned by [userId], or `PRIVATE` and
     * reachable through [SyncRepository.isRecipeHouseholdVisible] — the same household-sharing
     * gap the pull-side `/sync/pull` uses (§6.3). Everything else — nonexistent, soft-deleted, or
     * `PRIVATE` and neither owned nor shared — is [RecipeDetailResult.NotFound]. Deliberately
     * never a 403-shaped outcome: this mirrors the bookmark-push rule (see
     * docs/sync-protocol.md's validation table) of not distinguishing "doesn't exist" from
     * "exists but you can't see it," so a private recipe's existence isn't leaked to a caller
     * who isn't its owner.
     *
     * Reference data is fetched gap-only (`sinceMillis = null`) — the same mode [pushRecipes]
     * uses for conflict responses — since there's no pull cursor for a one-off fetch.
     * [RecipeDetailResult.Found.creators] is included alongside [SyncReferenceData] because
     * `recipes.creator_id` is itself an FK a client must be able to resolve, same as
     * [pullRecipes]'s `creators` field.
     */
    suspend fun getRecipeDetail(userId: UUID?, recipeId: UUID): RecipeDetailResult {
        val record = syncRepository.getRecipe(recipeId) ?: return RecipeDetailResult.NotFound
        val recipe = record.recipe

        if (recipe.deletedAt != null) return RecipeDetailResult.NotFound

        val isOwner = userId != null && recipe.creatorId == userId.toString()
        val isHouseholdVisible = userId != null && syncRepository.isRecipeHouseholdVisible(userId, recipeId)
        if (recipe.privacy != "PUBLIC" && !isOwner && !isHouseholdVisible) return RecipeDetailResult.NotFound

        val referenceData = syncRepository.collectReferenceData(
            ingredientIds = recipe.ingredients.map { UUID.fromString(it.ingredientId) }.toSet(),
            tagIds = recipe.tagIds.map { UUID.fromString(it) }.toSet(),
            labelIds = recipe.labelIds.map { UUID.fromString(it) }.toSet(),
            sinceMillis = null
        )
        val creators = syncRepository.collectCreators(
            creatorIds = setOf(UUID.fromString(recipe.creatorId)),
            sinceMillis = null
        )

        return RecipeDetailResult.Found(recipe, referenceData, creators)
    }

    /**
     * Batched counterpart to [getRecipeDetail], used to hydrate the recipes a stateless-generated
     * meal plan references (see `MealPlanGenerationService.generateStateless`). Fetches each of
     * [recipeIds] individually via [getRecipeDetail] and merges the results, de-duplicating
     * reference entities and creators shared across recipes by `uuid` — a plan's dinner and lunch
     * picks commonly share tags/labels/ingredients, and repeating them would just bloat the
     * response. An id that resolves to [RecipeDetailResult.NotFound] (e.g. deleted between
     * generation and this call) is silently omitted rather than failing the whole batch.
     */
    suspend fun getRecipeDetails(userId: UUID?, recipeIds: Set<UUID>): RecipeDetailsBundle {
        val found = recipeIds.mapNotNull { id ->
            getRecipeDetail(userId, id) as? RecipeDetailResult.Found
        }

        return RecipeDetailsBundle(
            recipes = found.map { it.recipe },
            referenceData = SyncReferenceData(
                ingredients = found.flatMap { it.referenceData.ingredients }.distinctBy { it.uuid },
                allergens = found.flatMap { it.referenceData.allergens }.distinctBy { it.uuid },
                sourceClassifications = found.flatMap { it.referenceData.sourceClassifications }.distinctBy { it.uuid },
                tags = found.flatMap { it.referenceData.tags }.distinctBy { it.uuid },
                labels = found.flatMap { it.referenceData.labels }.distinctBy { it.uuid }
            ),
            creators = found.flatMap { it.creators }.distinctBy { it.uuid }
        )
    }

    /**
     * Fetches a delta page of recipes whose boundary `serverUpdatedAtMillis` is never split
     * across two pages.
     *
     * `/sync/pull`'s cursor is a single millisecond `Long`. Splitting a page in the middle of
     * several rows that share one `server_updated_at` value is what gets a pull cursor stuck:
     * the next pull's `since` (`>` that shared value) either re-admits rows already returned —
     * if communicating the cursor lost precision, as `Clock.System.now()`'s microsecond
     * resolution used to before [millisecondPrecisionNow] — or silently drops whatever else
     * shared it. Either way the client stops making forward progress. This fetches one row past
     * `limit` to detect a tie at the boundary and, if found, defers the whole tied group to the
     * next page — widening the fetch when needed to find where a tie starting at `since` itself
     * actually ends, since there's no earlier material to defer to in that case.
     */
    private suspend fun fetchStablePage(
        userId: UUID,
        sinceMillis: Long,
        limit: Int
    ): Pair<List<SyncRecipeRecord>, Boolean> {
        val fetched = syncRepository.findDeltaRecipes(userId, sinceMillis, limit = limit + 1)
        if (fetched.size <= limit) return fetched to false

        val boundaryTimestamp = fetched[limit - 1].serverUpdatedAtMillis
        if (fetched[limit].serverUpdatedAtMillis != boundaryTimestamp) return fetched.take(limit) to true

        val earlierGroups = fetched.filter { it.serverUpdatedAtMillis < boundaryTimestamp }
        if (earlierGroups.isNotEmpty()) return earlierGroups to true

        // The tie starts at `since` itself, so there's no earlier material to cut at — the only
        // way to advance the cursor is to return the tied group whole, however large it turns
        // out to be. Widen the fetch to find where it ends.
        val widened = syncRepository.findDeltaRecipes(userId, sinceMillis, limit = TIE_ESCAPE_FETCH_LIMIT)
        val tiedGroup = widened.takeWhile { it.serverUpdatedAtMillis == boundaryTimestamp }
        return when {
            tiedGroup.size < widened.size -> tiedGroup to true
            widened.size < TIE_ESCAPE_FETCH_LIMIT -> widened to false
            else -> {
                // More than TIE_ESCAPE_FETCH_LIMIT recipes share one exact millisecond starting
                // at `since`. A single-timestamp cursor can't express "partway through a tie
                // this large" — fully resolving it needs a compound (timestamp, id) cursor,
                // out of scope here. Log it so a stuck account is diagnosable.
                log.warn(
                    "Sync pull for user $userId: recipe tie at server_updated_at=$boundaryTimestamp " +
                        "exceeds the $TIE_ESCAPE_FETCH_LIMIT-row safety fetch — cursor may not " +
                        "advance past it until the tie clears"
                )
                widened.take(limit) to true
            }
        }
    }

    /**
     * Validates ingredient references in a pushed recipe aggregate.
     */
    private suspend fun validateIngredients(
        recipe: SyncRecipe,
        errors: MutableList<SyncError>
    ): Boolean {
        for (ingredient in recipe.ingredients) {
            val ingredientId = parseUuid(ingredient.ingredientId) {
                errors += SyncError(
                    recipe.uuid,
                    SyncErrors.INVALID_INGREDIENT,
                    SyncErrors.INVALID_INGREDIENT.message
                )
            } ?: return false

            if (!syncRepository.ingredientExists(ingredientId)) {
                errors += SyncError(
                    recipe.uuid,
                    SyncErrors.INGREDIENT_NOT_FOUND,
                    SyncErrors.INGREDIENT_NOT_FOUND.message
                )
                return false
            }
        }
        return true
    }

    /**
     * Validates tag/label UUID formats.
     *
     * Unknown but well-formed IDs are allowed and ignored later by repository upsert.
     */
    private fun validateTagAndLabelIds(
        recipe: SyncRecipe,
        errors: MutableList<SyncError>
    ): Boolean {
        for (tagId in recipe.tagIds) {
            val isValid = parseUuid(tagId) {
                errors += SyncError(
                    recipe.uuid,
                    SyncErrors.INVALID_TAG,
                    SyncErrors.INVALID_TAG.message
                )
            } != null
            if (!isValid) return false
        }

        for (labelId in recipe.labelIds) {
            val isValid = parseUuid(labelId) {
                errors += SyncError(
                    recipe.uuid,
                    SyncErrors.INVALID_LABEL,
                    SyncErrors.INVALID_LABEL.message
                )
            } != null
            if (!isValid) return false
        }

        return true
    }

    /**
     * Parses a UUID and runs a callback on parsing failure.
     */
    private inline fun parseUuid(value: String, onFailure: () -> Unit): UUID? =
        try {
            UUID.fromString(value)
        } catch (_: IllegalArgumentException) {
            onFailure()
            null
        }

    private fun recipeClientUpdatedAt(recipeId: String, request: SyncPushRequest): Long? =
        request.recipes.firstOrNull { it.uuid == recipeId }?.updatedAt
}
