package com.tenmilelabs.domain.service

import com.tenmilelabs.application.dto.AcceptedEntity
import com.tenmilelabs.application.dto.ConflictEntity
import com.tenmilelabs.application.dto.ConflictReasons
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

        log.info(
            "Sync push processed for user $userId: accepted=${accepted.size}, conflicts=${conflicts.size}, errors=${errors.size}"
        )
        // TODO Remove before release, or put behind debug flag.
        for (acceptedRecipe in accepted) {
            log.debug(
                "Sync push accepted for user $userId, recipeId=${acceptedRecipe.uuid}, " +
                    "serverUpdatedAt=${acceptedRecipe.serverUpdatedAt}"
            )
        }

        for (conflict in conflicts) {
            log.debug(
                "Sync push conflicted for user $userId, recipeId=${conflict.uuid}, " +
                    "reason=${conflict.reason}, clientUpdatedAt=${recipeClientUpdatedAt(conflict.uuid, request)}, " +
                    "serverUpdatedAt=${conflict.serverVersion.updatedAt}"
            )
        }

        for (error in errors) {
            log.debug(
                "Sync push errored for user $userId, recipeId=${error.uuid}, " +
                    "reason=${error.reason}, message=${error.message}"
            )
        }

        return SyncPushResponse(
            accepted = accepted,
            conflicts = conflicts,
            errors = errors,
            serverTimestamp = Clock.System.now().toEpochMilliseconds(),
            referenceData = conflictReferenceData
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

        log.info(
            "Sync pull for user $userId: recipes=${page.size}, hasMore=$hasMore, " +
                "ingredients=${refData.ingredients.size}, allergens=${refData.allergens.size}, " +
                "sourceClassifications=${refData.sourceClassifications.size}, " +
                "tags=${refData.tags.size}, labels=${refData.labels.size}"
        )

        return SyncPullResponse(
            recipes = page.map { it.recipe },
            ingredients = refData.ingredients,
            allergens = refData.allergens,
            sourceClassifications = refData.sourceClassifications,
            tags = refData.tags,
            labels = refData.labels,
            serverTimestamp = cursor,
            hasMore = hasMore
        )
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
