package com.tenmilelabs.domain.service

import com.tenmilelabs.application.dto.AcceptedEntity
import com.tenmilelabs.application.dto.BookmarkSyncError
import com.tenmilelabs.application.dto.BookmarkSyncErrors
import com.tenmilelabs.application.dto.ConflictEntity
import com.tenmilelabs.application.dto.ConflictReasons
import com.tenmilelabs.application.dto.SyncBookmarkedRecipe
import com.tenmilelabs.application.dto.SyncError
import com.tenmilelabs.application.dto.SyncErrors
import com.tenmilelabs.application.dto.SyncPullResponse
import com.tenmilelabs.application.dto.SyncPushRequest
import com.tenmilelabs.application.dto.SyncPushResponse
import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.application.dto.SyncReferenceData
import com.tenmilelabs.domain.repository.SyncRepository
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

class SyncService(
    private val syncRepository: SyncRepository,
    private val log: Logger
) {
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

            val now = Clock.System.now()
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

        val (acceptedBookmarks, bookmarkErrors) = processBookmarks(userId, request)

        log.info(
            "Sync push processed for user $userId: accepted=${accepted.size}, conflicts=${conflicts.size}, " +
                "errors=${errors.size}, acceptedBookmarks=${acceptedBookmarks.size}, bookmarkErrors=${bookmarkErrors.size}"
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

        return SyncPushResponse(
            accepted = accepted,
            conflicts = conflicts,
            errors = errors,
            serverTimestamp = Clock.System.now().toEpochMilliseconds(),
            referenceData = conflictReferenceData,
            acceptedBookmarks = acceptedBookmarks,
            bookmarkErrors = bookmarkErrors
        )
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

        val records = syncRepository.findDeltaRecipes(
            userId = userId,
            sinceMillis = sinceMillis,
            limit = limit + 1
        )
        val hasMore = records.size > limit
        val page = if (hasMore) records.take(limit) else records
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

        val bookmarks = syncRepository.findDeltaBookmarks(userId, sinceMillis)

        log.info(
            "Sync pull for user $userId: recipes=${page.size}, hasMore=$hasMore, " +
                "ingredients=${refData.ingredients.size}, allergens=${refData.allergens.size}, " +
                "sourceClassifications=${refData.sourceClassifications.size}, " +
                "tags=${refData.tags.size}, labels=${refData.labels.size}, bookmarks=${bookmarks.size}"
        )

        return SyncPullResponse(
            recipes = page.map { it.recipe },
            ingredients = refData.ingredients,
            allergens = refData.allergens,
            sourceClassifications = refData.sourceClassifications,
            tags = refData.tags,
            labels = refData.labels,
            bookmarkedRecipes = bookmarks,
            serverTimestamp = cursor,
            hasMore = hasMore
        )
    }

    /**
     * Processes bookmarked_recipes from a push request.
     *
     * Validates user ownership and recipe access (PUBLIC or creator-owned).
     * Always accepts valid bookmarks using last-writer-wins semantics; no conflict
     * is reported for bookmarks. Returns a pair of (accepted, errors).
     */
    private suspend fun processBookmarks(
        userId: UUID,
        request: SyncPushRequest
    ): Pair<List<SyncBookmarkedRecipe>, List<BookmarkSyncError>> {
        val accepted = mutableListOf<SyncBookmarkedRecipe>()
        val errors = mutableListOf<BookmarkSyncError>()

        request.bookmarkedRecipes.forEach { bookmark ->
            val bookmarkUserId = parseUuid(bookmark.userId) {
                errors += BookmarkSyncError(
                    userId = bookmark.userId,
                    recipeId = bookmark.recipeId,
                    reason = BookmarkSyncErrors.INVALID_USER_ID,
                    message = BookmarkSyncErrors.INVALID_USER_ID.message
                )
            } ?: return@forEach

            if (bookmarkUserId != userId) {
                errors += BookmarkSyncError(
                    userId = bookmark.userId,
                    recipeId = bookmark.recipeId,
                    reason = BookmarkSyncErrors.USER_MISMATCH,
                    message = BookmarkSyncErrors.USER_MISMATCH.message
                )
                return@forEach
            }

            val recipeId = parseUuid(bookmark.recipeId) {
                errors += BookmarkSyncError(
                    userId = bookmark.userId,
                    recipeId = bookmark.recipeId,
                    reason = BookmarkSyncErrors.INVALID_RECIPE_ID,
                    message = BookmarkSyncErrors.INVALID_RECIPE_ID.message
                )
            } ?: return@forEach

            val existing = syncRepository.getRecipe(recipeId)
            if (existing == null) {
                errors += BookmarkSyncError(
                    userId = bookmark.userId,
                    recipeId = bookmark.recipeId,
                    reason = BookmarkSyncErrors.RECIPE_NOT_FOUND,
                    message = BookmarkSyncErrors.RECIPE_NOT_FOUND.message
                )
                return@forEach
            }

            if (existing.recipe.privacy == "PRIVATE" && existing.recipe.creatorId != userId.toString()) {
                errors += BookmarkSyncError(
                    userId = bookmark.userId,
                    recipeId = bookmark.recipeId,
                    reason = BookmarkSyncErrors.RECIPE_ACCESS_DENIED,
                    message = BookmarkSyncErrors.RECIPE_ACCESS_DENIED.message
                )
                return@forEach
            }

            val now = Clock.System.now()
            val deletedAt = bookmark.deletedAt?.let { Instant.fromEpochMilliseconds(it) }
            syncRepository.upsertBookmark(userId, recipeId, now, deletedAt)

            accepted += SyncBookmarkedRecipe(
                userId = bookmark.userId,
                recipeId = bookmark.recipeId,
                updatedAt = now.toEpochMilliseconds(),
                deletedAt = bookmark.deletedAt,
                syncState = "SYNCED"
            )
        }

        return accepted to errors
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
