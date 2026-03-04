package domain.service

import com.tenmilelabs.application.dto.SyncIngredient
import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.domain.repository.SyncRecipeRecord
import com.tenmilelabs.domain.repository.SyncRepository
import kotlinx.datetime.Instant
import java.util.UUID

class FakeSyncRepository : SyncRepository {
    private val recipes = mutableMapOf<UUID, SyncRecipeRecord>()
    private val ingredients = mutableMapOf<UUID, SyncIngredient>()
    private val knownTagIds = mutableSetOf<UUID>()
    private val knownLabelIds = mutableSetOf<UUID>()

    fun seedIngredient(uuid: UUID = UUID.randomUUID()): UUID {
        ingredients[uuid] = SyncIngredient(
            uuid = uuid.toString(),
            displayName = "Ingredient-${uuid.toString().take(8)}",
            allergenId = null,
            sourcePrimaryId = null,
            updatedAt = 0L,
            deletedAt = null
        )
        return uuid
    }

    fun seedRecipe(recipe: SyncRecipe, serverUpdatedAtMillis: Long) {
        recipes[UUID.fromString(recipe.uuid)] = SyncRecipeRecord(recipe, serverUpdatedAtMillis)
    }

    override suspend fun getRecipe(uuid: UUID): SyncRecipeRecord? = recipes[uuid]

    override suspend fun upsertRecipeAggregate(recipe: SyncRecipe, serverUpdatedAt: Instant) {
        recipes[UUID.fromString(recipe.uuid)] = SyncRecipeRecord(recipe, serverUpdatedAt.toEpochMilliseconds())
    }

    override suspend fun findDeltaRecipes(userId: UUID, sinceMillis: Long, limit: Int): List<SyncRecipeRecord> =
        recipes.values
            .filter {
                it.serverUpdatedAtMillis > sinceMillis &&
                    (it.recipe.creatorId == userId.toString() || it.recipe.privacy == "PUBLIC")
            }
            .sortedBy { it.serverUpdatedAtMillis }
            .take(limit)

    override suspend fun ingredientExists(uuid: UUID): Boolean = ingredients.containsKey(uuid)

    override suspend fun findIngredients(
        sinceMillis: Long,
        referencedIds: Set<UUID>
    ): List<SyncIngredient> = ingredients
        .filter { (uuid, ingredient) -> ingredient.updatedAt > sinceMillis || uuid in referencedIds }
        .values
        .sortedWith(compareBy<SyncIngredient> { it.updatedAt }.thenBy { it.uuid })

    override suspend fun existingTagIds(ids: Set<UUID>): Set<UUID> = ids.intersect(knownTagIds)

    override suspend fun existingLabelIds(ids: Set<UUID>): Set<UUID> = ids.intersect(knownLabelIds)
}
