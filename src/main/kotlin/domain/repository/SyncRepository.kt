package com.tenmilelabs.domain.repository

import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.application.dto.SyncReferenceData
import kotlinx.datetime.Instant
import java.util.UUID

data class SyncRecipeRecord(
    val recipe: SyncRecipe,
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
        sinceMillis: Long?,
        ingredientIds: Set<UUID>,
        tagIds: Set<UUID>,
        labelIds: Set<UUID>
    ): SyncReferenceData

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
}
