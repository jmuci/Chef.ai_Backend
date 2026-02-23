package com.tenmilelabs.domain.repository

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
    suspend fun existingTagIds(ids: Set<UUID>): Set<UUID>
    suspend fun existingLabelIds(ids: Set<UUID>): Set<UUID>
}
