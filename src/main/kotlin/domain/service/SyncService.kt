package com.tenmilelabs.domain.service

import com.tenmilelabs.application.dto.AcceptedEntity
import com.tenmilelabs.application.dto.BookmarkErrors
import com.tenmilelabs.application.dto.BookmarkPushError
import com.tenmilelabs.application.dto.BookmarkPushResult
import com.tenmilelabs.application.dto.ConflictEntity
import com.tenmilelabs.application.dto.ConflictReasons
import com.tenmilelabs.application.dto.MealPlanPushResult
import com.tenmilelabs.application.dto.MealPlanPushResults
import com.tenmilelabs.application.dto.SyncError
import com.tenmilelabs.application.dto.SyncErrors
import com.tenmilelabs.application.dto.SyncMealPlanDto
import com.tenmilelabs.application.dto.SyncPullResponse
import com.tenmilelabs.application.dto.SyncPushRequest
import com.tenmilelabs.application.dto.SyncPushResponse
import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.application.dto.SyncReferenceData
import com.tenmilelabs.domain.repository.SyncRecipeRecord
import com.tenmilelabs.domain.repository.SyncRepository
import com.tenmilelabs.domain.repository.UserPreferencesRepository
import com.tenmilelabs.domain.util.millisecondPrecisionNow
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

class SyncService(
    private val syncRepository: SyncRepository,
    private val log: Logger,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    /**
     * Safety cap on how far [fetchStablePage] will widen a fetch to find the end of a
     * `server_updated_at` tie that starts at the pull's own `since` cursor.
     */
    private companion object {
        private const val TIE_ESCAPE_FETCH_LIMIT = 5_000
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

        return SyncPushResponse(
            accepted = accepted,
            conflicts = conflicts,
            errors = errors,
            serverTimestamp = Clock.System.now().toEpochMilliseconds(),
            referenceData = conflictReferenceData,
            bookmarkedRecipes = bookmarkResults,
            bookmarkErrors = bookmarkErrors,
            mealPlans = mealPlanResults
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

            val existing = syncRepository.getMealPlanForUser(planUuid, userId)
            if (existing != null && existing.serverUpdatedAtMillis > plan.updatedAt) {
                conflicts += plan.uuid
                log.info("Meal plan push conflict for user $userId, planId=${plan.uuid}: server is newer")
                return@forEach
            }

            val now = millisecondPrecisionNow()
            syncRepository.upsertMealPlan(plan, userId, now)
            userPreferencesRepository.upsertUserPreferences(userId, plan.preferencesJson, now)
            accepted += MealPlanPushResult(uuid = plan.uuid, serverUpdatedAt = now.toEpochMilliseconds())
            log.info("Meal plan push accepted for user $userId, planId=${plan.uuid}")
        }

        return MealPlanPushResults(accepted = accepted, conflicts = conflicts, errors = errors)
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

        val (page, hasMore) = fetchStablePage(userId, sinceMillis, limit)
        val cursor = page.lastOrNull()?.serverUpdatedAtMillis ?: sinceMillis

        val ingredientIds = page
            .flatMap { it.recipe.ingredients }
            .map { UUID.fromString(it.ingredientId) }
            .toSet()
        val tagIds = page
            .flatMap { it.recipe.tagIds }
            .map { UUID.fromString(it) }
            .toSet()
        val labelIds = page
            .flatMap { it.recipe.labelIds }
            .map { UUID.fromString(it) }
            .toSet()
        val refData = syncRepository.collectReferenceData(
            sinceMillis = sinceMillis,
            ingredientIds = ingredientIds,
            tagIds = tagIds,
            labelIds = labelIds
        )
        val creatorIds = page
            .map { UUID.fromString(it.recipe.creatorId) }
            .toSet()
        val creators = syncRepository.collectCreators(
            creatorIds = creatorIds,
            sinceMillis = sinceMillis
        )

        val bookmarks = syncRepository.findDeltaBookmarks(userId, sinceMillis)
        val mealPlans = syncRepository.findDeltaMealPlans(userId, sinceMillis)

        log.info(
            "Sync pull for user $userId: recipes=${page.size}, hasMore=$hasMore, " +
                "ingredients=${refData.ingredients.size}, allergens=${refData.allergens.size}, " +
                "sourceClassifications=${refData.sourceClassifications.size}, " +
                "tags=${refData.tags.size}, labels=${refData.labels.size}, " +
                "bookmarks=${bookmarks.size}, mealPlans=${mealPlans.size}"
        )

        return SyncPullResponse(
            recipes = page.map { it.recipe },
            creators = creators,
            ingredients = refData.ingredients,
            allergens = refData.allergens,
            sourceClassifications = refData.sourceClassifications,
            tags = refData.tags,
            labels = refData.labels,
            bookmarkedRecipes = bookmarks,
            mealPlans = mealPlans.map { it.plan },
            serverTimestamp = cursor,
            hasMore = hasMore
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
