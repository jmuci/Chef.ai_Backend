package com.tenmilelabs.domain.repository

import com.tenmilelabs.application.dto.SyncIngredient
import com.tenmilelabs.application.dto.SyncRecipe
import kotlinx.datetime.Instant
import java.util.UUID

data class SyncRecipeRecord(
    val recipe: SyncRecipe,
    val serverUpdatedAtMillis: Long
)

interface SyncRepository {
    suspend fun getRecipe(uuid: UUID): SyncRecipeRecord?
    suspend fun upsertRecipeAggregate(recipe: SyncRecipe, serverUpdatedAt: Instant)
    suspend fun findDeltaRecipes(
        userId: UUID,
        sinceMillis: Long,
        limit: Int
    ): List<SyncRecipeRecord>
    suspend fun ingredientExists(uuid: UUID): Boolean
    /**
     * Returns the union of:
     *  - all ingredients with server_updated_at > sinceMillis (delta)
     *  - all ingredients whose uuid is in [referencedIds] (gap coverage)
     *
     * This guarantees referential completeness for the client without
     * requiring per-client state: gap ingredients are only included when
     * a recipe in the current page references them, so unchanged ingredients
     * that the client already has are not re-sent on every pull.
     */
    suspend fun findIngredients(sinceMillis: Long, referencedIds: Set<UUID>): List<SyncIngredient>
    suspend fun existingTagIds(ids: Set<UUID>): Set<UUID>
    suspend fun existingLabelIds(ids: Set<UUID>): Set<UUID>
}
