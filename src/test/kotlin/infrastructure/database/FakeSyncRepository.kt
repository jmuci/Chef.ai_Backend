package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.domain.repository.SyncRecipeRecord
import com.tenmilelabs.domain.repository.SyncRepository
import kotlinx.datetime.Instant
import java.util.UUID

class FakeSyncRepository : SyncRepository {
    private val recipes = mutableMapOf<UUID, SyncRecipeRecord>()
    private val ingredientIds = mutableSetOf<UUID>()
    private val knownTagIds = mutableSetOf<UUID>()
    private val knownLabelIds = mutableSetOf<UUID>()

    fun seedIngredient(uuid: UUID = UUID.randomUUID()): UUID {
        ingredientIds += uuid
        return uuid
    }

    fun seedRecipe(recipe: SyncRecipe, serverUpdatedAtMillis: Long) {
        recipes[UUID.fromString(recipe.uuid)] = SyncRecipeRecord(
            recipe = recipe,
            serverUpdatedAtMillis = serverUpdatedAtMillis
        )
    }

    fun seedTag(uuid: UUID = UUID.randomUUID()): UUID {
        knownTagIds += uuid
        return uuid
    }

    fun seedLabel(uuid: UUID = UUID.randomUUID()): UUID {
        knownLabelIds += uuid
        return uuid
    }

    override suspend fun getRecipe(uuid: UUID): SyncRecipeRecord? = recipes[uuid]

    override suspend fun upsertRecipeAggregate(recipe: SyncRecipe, serverUpdatedAt: Instant) {
        recipes[UUID.fromString(recipe.uuid)] = SyncRecipeRecord(
            recipe = recipe,
            serverUpdatedAtMillis = serverUpdatedAt.toEpochMilliseconds()
        )
    }

    override suspend fun findDeltaRecipes(userId: UUID, sinceMillis: Long, limit: Int): List<SyncRecipeRecord> =
        recipes.values
            .filter {
                it.serverUpdatedAtMillis > sinceMillis &&
                    (it.recipe.creatorId == userId.toString() || it.recipe.privacy == "PUBLIC")
            }
            .sortedBy { it.serverUpdatedAtMillis }
            .take(limit)

    override suspend fun ingredientExists(uuid: UUID): Boolean = ingredientIds.contains(uuid)

    override suspend fun existingTagIds(ids: Set<UUID>): Set<UUID> = ids.intersect(knownTagIds)

    override suspend fun existingLabelIds(ids: Set<UUID>): Set<UUID> = ids.intersect(knownLabelIds)
}
