package domain.service

import com.tenmilelabs.application.dto.SyncBookmark
import com.tenmilelabs.application.dto.SyncIngredient
import com.tenmilelabs.application.dto.SyncLabel
import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.application.dto.SyncReferenceData
import com.tenmilelabs.application.dto.SyncTag
import com.tenmilelabs.application.dto.SyncUser
import com.tenmilelabs.domain.repository.SyncRecipeRecord
import com.tenmilelabs.domain.repository.SyncRepository
import kotlinx.datetime.Instant
import java.util.UUID

class FakeSyncRepository : SyncRepository {
    private val recipes = mutableMapOf<UUID, SyncRecipeRecord>()
    private val ingredients = mutableMapOf<UUID, SyncIngredient>()
    private val ingredientServerTs = mutableMapOf<UUID, Long>()
    private val tags = mutableMapOf<UUID, SyncTag>()
    private val tagServerTs = mutableMapOf<UUID, Long>()
    private val labels = mutableMapOf<UUID, SyncLabel>()
    private val labelServerTs = mutableMapOf<UUID, Long>()
    private val users = mutableMapOf<UUID, SyncUser>()
    private val userServerTs = mutableMapOf<UUID, Long>()
    // key: (userId, recipeId), value: bookmark with server-stamped updatedAt
    private val bookmarks = mutableMapOf<Pair<UUID, UUID>, SyncBookmark>()
    private val bookmarkServerTs = mutableMapOf<Pair<UUID, UUID>, Long>()
    private val inaccessibleRecipes = mutableSetOf<UUID>()

    fun seedIngredient(uuid: UUID = UUID.randomUUID(), serverUpdatedAt: Long = 0L): UUID {
        ingredients[uuid] = SyncIngredient(
            uuid = uuid.toString(),
            displayName = "Ingredient-${uuid.toString().take(8)}",
            allergenId = null,
            sourcePrimaryId = null,
            updatedAt = serverUpdatedAt,
            deletedAt = null
        )
        ingredientServerTs[uuid] = serverUpdatedAt
        return uuid
    }

    fun seedTag(uuid: UUID = UUID.randomUUID(), serverUpdatedAt: Long = 0L): UUID {
        tags[uuid] = SyncTag(
            uuid = uuid.toString(),
            displayName = "Tag-${uuid.toString().take(8)}",
            updatedAt = serverUpdatedAt,
            deletedAt = null
        )
        tagServerTs[uuid] = serverUpdatedAt
        return uuid
    }

    fun seedLabel(uuid: UUID = UUID.randomUUID(), serverUpdatedAt: Long = 0L): UUID {
        labels[uuid] = SyncLabel(
            uuid = uuid.toString(),
            displayName = "Label-${uuid.toString().take(8)}",
            updatedAt = serverUpdatedAt,
            deletedAt = null
        )
        labelServerTs[uuid] = serverUpdatedAt
        return uuid
    }

    fun seedRecipe(recipe: SyncRecipe, serverUpdatedAtMillis: Long) {
        val creatorId = UUID.fromString(recipe.creatorId)
        if (!users.containsKey(creatorId)) {
            seedUser(uuid = creatorId, serverUpdatedAt = 0L)
        }
        recipes[UUID.fromString(recipe.uuid)] = SyncRecipeRecord(recipe, serverUpdatedAtMillis)
    }

    fun seedUser(uuid: UUID = UUID.randomUUID(), serverUpdatedAt: Long = 0L): UUID {
        users[uuid] = SyncUser(
            uuid = uuid.toString(),
            displayName = "User-${uuid.toString().take(8)}",
            email = "${uuid.toString().take(8)}@example.com",
            avatarUrl = "",
            updatedAt = serverUpdatedAt,
            deletedAt = null
        )
        userServerTs[uuid] = serverUpdatedAt
        return uuid
    }

    fun makeRecipeInaccessible(recipeId: UUID) {
        inaccessibleRecipes += recipeId
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

    override suspend fun collectReferenceData(
        ingredientIds: Set<UUID>,
        tagIds: Set<UUID>,
        labelIds: Set<UUID>,
        sinceMillis: Long?
    ): SyncReferenceData {
        // Mirrors the union logic in PostgresSyncRepository:
        // include an entity if it changed since the cursor (delta) OR is directly referenced (gap).
        fun <V> Map<UUID, V>.unionFilter(ids: Set<UUID>, serverTs: Map<UUID, Long>): List<V> =
            filter { (uuid, _) ->
                (sinceMillis != null && (serverTs[uuid] ?: 0L) > sinceMillis) || uuid in ids
            }.values.toList()

        return SyncReferenceData(
            ingredients = ingredients.unionFilter(ingredientIds, ingredientServerTs),
            allergens = emptyList(),          // no allergen catalogue in this fake
            sourceClassifications = emptyList(),
            tags = tags.unionFilter(tagIds, tagServerTs),
            labels = labels.unionFilter(labelIds, labelServerTs)
        )
    }

    override suspend fun existingTagIds(ids: Set<UUID>): Set<UUID> = ids.intersect(tags.keys)

    override suspend fun existingLabelIds(ids: Set<UUID>): Set<UUID> = ids.intersect(labels.keys)

    override suspend fun collectCreators(creatorIds: Set<UUID>, sinceMillis: Long?): List<SyncUser> {
        fun <V> Map<UUID, V>.unionFilter(ids: Set<UUID>, serverTs: Map<UUID, Long>): List<V> =
            filter { (uuid, _) ->
                (sinceMillis != null && (serverTs[uuid] ?: 0L) > sinceMillis) || uuid in ids
            }.values.toList()

        return users.unionFilter(creatorIds, userServerTs)
    }

    override suspend fun isRecipeAccessibleBy(userId: UUID, recipeId: UUID): Boolean {
        if (recipeId in inaccessibleRecipes) return false
        val record = recipes[recipeId] ?: return false
        return record.recipe.creatorId == userId.toString() || record.recipe.privacy == "PUBLIC"
    }

    override suspend fun upsertBookmark(
        userId: UUID,
        recipeId: UUID,
        deletedAt: Instant?,
        serverUpdatedAt: Instant
    ) {
        val key = userId to recipeId
        val ts = serverUpdatedAt.toEpochMilliseconds()
        bookmarks[key] = SyncBookmark(
            userId = userId.toString(),
            recipeId = recipeId.toString(),
            updatedAt = ts,
            deletedAt = deletedAt?.toEpochMilliseconds()
        )
        bookmarkServerTs[key] = ts
    }

    override suspend fun findDeltaBookmarks(userId: UUID, sinceMillis: Long): List<SyncBookmark> =
        bookmarks.entries
            .filter { (key, _) ->
                key.first == userId && (bookmarkServerTs[key] ?: 0L) > sinceMillis
            }
            .map { it.value }
}
