package com.tenmilelabs.infrastructure.database.repositoryImpl

import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.application.dto.SyncRecipeIngredient
import com.tenmilelabs.application.dto.SyncRecipeStep
import com.tenmilelabs.domain.repository.SyncRecipeRecord
import com.tenmilelabs.domain.repository.SyncRepository
import com.tenmilelabs.infrastructure.database.mappers.suspendTransaction
import com.tenmilelabs.infrastructure.database.tables.*
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*

class PostgresSyncRepository : SyncRepository {
    /**
     * Loads one recipe aggregate by UUID.
     *
     * The aggregate includes the recipe root plus active children:
     * steps, ingredients, tags, and labels.
     */
    override suspend fun getRecipe(uuid: UUID): SyncRecipeRecord? = suspendTransaction {
        val recipeRow = RecipeTable
            .selectAll()
            .where { RecipeTable.id eq uuid }
            .firstOrNull()
            ?: return@suspendTransaction null

        toSyncRecipeRecord(recipeRow)
    }

    /**
     * Persists a full recipe aggregate using replace semantics for children.
     *
     * Existing child rows are deleted and re-inserted from the incoming payload
     * so the server snapshot matches the client aggregate atomically.
     */
    override suspend fun upsertRecipeAggregate(recipe: SyncRecipe, serverUpdatedAt: Instant) = suspendTransaction {
        val recipeUuid = UUID.fromString(recipe.uuid)
        val recipeEntityId = EntityID(recipeUuid, RecipeTable)

        val exists = RecipeTable
            .selectAll()
            .where { RecipeTable.id eq recipeUuid }
            .limit(1)
            .any()

        if (exists) {
            RecipeTable.update({ RecipeTable.id eq recipeUuid }) {
                it[title] = recipe.title
                it[description] = recipe.description
                it[image_url] = recipe.imageUrl
                it[image_url_thumbnail] = recipe.imageUrlThumbnail
                it[prep_time_minutes] = recipe.prepTimeMinutes
                it[cook_time_minutes] = recipe.cookTimeMinutes
                it[servings] = recipe.servings
                it[creator_id] = UUID.fromString(recipe.creatorId)
                it[recipe_external_url] = recipe.recipeExternalUrl
                it[privacy] = recipe.privacy
                it[updated_at] = recipe.updatedAt
                it[deleted_at] = recipe.deletedAt
                it[RecipeTable.server_updated_at] = serverUpdatedAt
            }
        } else {
            RecipeTable.insert {
                it[id] = recipeEntityId
                it[title] = recipe.title
                it[description] = recipe.description
                it[image_url] = recipe.imageUrl
                it[image_url_thumbnail] = recipe.imageUrlThumbnail
                it[prep_time_minutes] = recipe.prepTimeMinutes
                it[cook_time_minutes] = recipe.cookTimeMinutes
                it[servings] = recipe.servings
                it[creator_id] = UUID.fromString(recipe.creatorId)
                it[recipe_external_url] = recipe.recipeExternalUrl
                it[privacy] = recipe.privacy
                it[updated_at] = recipe.updatedAt
                it[deleted_at] = recipe.deletedAt
                it[RecipeTable.server_updated_at] = serverUpdatedAt
            }
        }

        RecipeStepTable.deleteWhere { RecipeStepTable.recipe_id eq recipeEntityId }
        RecipeIngredientTable.deleteWhere { RecipeIngredientTable.recipeId eq recipeEntityId }
        RecipeTagTable.deleteWhere { RecipeTagTable.recipeId eq recipeEntityId }
        RecipeLabelTable.deleteWhere { RecipeLabelTable.recipeId eq recipeEntityId }

        recipe.steps.forEach { step ->
            RecipeStepTable.insert {
                it[id] = EntityID(UUID.fromString(step.uuid), RecipeStepTable)
                it[recipe_id] = recipeEntityId
                it[order_index] = step.orderIndex
                it[instruction] = step.instruction
                it[updated_at] = recipe.updatedAt
                it[deleted_at] = recipe.deletedAt
                it[RecipeStepTable.server_updated_at] = serverUpdatedAt
            }
        }

        recipe.ingredients.forEach { ingredient ->
            val ingredientEntityId = EntityID(UUID.fromString(ingredient.ingredientId), IngredientTable)
            RecipeIngredientTable.insert {
                it[recipeId] = recipeEntityId
                it[ingredientId] = ingredientEntityId
                it[quantity] = ingredient.quantity
                it[unit] = ingredient.unit
                it[updatedAt] = recipe.updatedAt
                it[deletedAt] = recipe.deletedAt
                it[syncState] = "SYNCED"
                it[RecipeIngredientTable.serverUpdatedAt] = serverUpdatedAt
            }
        }

        val incomingTagIds = recipe.tagIds.map { UUID.fromString(it) }.toSet()
        val knownTagIds = if (incomingTagIds.isEmpty()) {
            emptySet()
        } else {
            val entityIds = incomingTagIds.map { EntityID(it, TagTable) }
            TagTable
                .selectAll()
                .where { TagTable.id inList entityIds }
                .map { it[TagTable.id].value }
                .toSet()
        }
        knownTagIds.forEach { tagId ->
            RecipeTagTable.insert {
                it[recipeId] = recipeEntityId
                it[RecipeTagTable.tagId] = EntityID(tagId, TagTable)
                it[updatedAt] = recipe.updatedAt
                it[deletedAt] = recipe.deletedAt
                it[syncState] = "SYNCED"
                it[RecipeTagTable.serverUpdatedAt] = serverUpdatedAt
            }
        }

        val incomingLabelIds = recipe.labelIds.map { UUID.fromString(it) }.toSet()
        val knownLabelIds = if (incomingLabelIds.isEmpty()) {
            emptySet()
        } else {
            val entityIds = incomingLabelIds.map { EntityID(it, LabelTable) }
            LabelTable
                .selectAll()
                .where { LabelTable.id inList entityIds }
                .map { it[LabelTable.id].value }
                .toSet()
        }
        knownLabelIds.forEach { labelId ->
            RecipeLabelTable.insert {
                it[recipeId] = recipeEntityId
                it[RecipeLabelTable.labelId] = EntityID(labelId, LabelTable)
                it[updatedAt] = recipe.updatedAt
                it[deletedAt] = recipe.deletedAt
                it[syncState] = "SYNCED"
                it[RecipeLabelTable.serverUpdatedAt] = serverUpdatedAt
            }
        }
    }

    /**
     * Returns recipe aggregates changed after `sinceMillis`, scoped to
     * authenticated user ownership plus PUBLIC visibility.
     *
     * Results are ordered by `server_updated_at` ascending for deterministic paging.
     */
    override suspend fun findDeltaRecipes(
        userId: UUID,
        sinceMillis: Long,
        limit: Int
    ): List<SyncRecipeRecord> = suspendTransaction {
        val sinceInstant = Instant.fromEpochMilliseconds(sinceMillis)
        RecipeTable
            .selectAll()
            .where {
                (RecipeTable.server_updated_at greater sinceInstant) and
                    ((RecipeTable.creator_id eq userId) or (RecipeTable.privacy eq "PUBLIC"))
            }
            .orderBy(RecipeTable.server_updated_at to SortOrder.ASC)
            .limit(limit)
            .map(::toSyncRecipeRecord)
    }

    override suspend fun ingredientExists(uuid: UUID): Boolean = suspendTransaction {
        IngredientTable.selectAll().where { IngredientTable.id eq uuid }.limit(1).any()
    }

    /**
     * Resolves a set of tag UUIDs to only those present in the catalog.
     */
    override suspend fun existingTagIds(ids: Set<UUID>): Set<UUID> = suspendTransaction {
        if (ids.isEmpty()) return@suspendTransaction emptySet()
        val entityIds = ids.map { EntityID(it, TagTable) }
        TagTable
            .selectAll()
            .where { TagTable.id inList entityIds }
            .map { it[TagTable.id].value }
            .toSet()
    }

    /**
     * Resolves a set of label UUIDs to only those present in the catalog.
     */
    override suspend fun existingLabelIds(ids: Set<UUID>): Set<UUID> = suspendTransaction {
        if (ids.isEmpty()) return@suspendTransaction emptySet()
        val entityIds = ids.map { EntityID(it, LabelTable) }
        LabelTable
            .selectAll()
            .where { LabelTable.id inList entityIds }
            .map { it[LabelTable.id].value }
            .toSet()
    }

    /**
     * Maps one recipe row into a sync aggregate payload plus server cursor value.
     */
    private fun toSyncRecipeRecord(recipeRow: ResultRow): SyncRecipeRecord {
        val recipeId = recipeRow[RecipeTable.id].value
        val recipeEntityId = EntityID(recipeId, RecipeTable)

        val steps = RecipeStepTable
            .selectAll()
            .where { (RecipeStepTable.recipe_id eq recipeEntityId) and (RecipeStepTable.deleted_at.isNull()) }
            .orderBy(RecipeStepTable.order_index to SortOrder.ASC)
            .map {
                SyncRecipeStep(
                    uuid = it[RecipeStepTable.id].value.toString(),
                    orderIndex = it[RecipeStepTable.order_index],
                    instruction = it[RecipeStepTable.instruction]
                )
            }

        val ingredients = RecipeIngredientTable
            .selectAll()
            .where { (RecipeIngredientTable.recipeId eq recipeEntityId) and (RecipeIngredientTable.deletedAt.isNull()) }
            .map {
                SyncRecipeIngredient(
                    ingredientId = it[RecipeIngredientTable.ingredientId].value.toString(),
                    quantity = it[RecipeIngredientTable.quantity],
                    unit = it[RecipeIngredientTable.unit]
                )
            }

        val tagIds = RecipeTagTable
            .selectAll()
            .where { (RecipeTagTable.recipeId eq recipeEntityId) and (RecipeTagTable.deletedAt.isNull()) }
            .map { it[RecipeTagTable.tagId].value.toString() }

        val labelIds = RecipeLabelTable
            .selectAll()
            .where { (RecipeLabelTable.recipeId eq recipeEntityId) and (RecipeLabelTable.deletedAt.isNull()) }
            .map { it[RecipeLabelTable.labelId].value.toString() }

        val recipe = SyncRecipe(
            uuid = recipeId.toString(),
            title = recipeRow[RecipeTable.title],
            description = recipeRow[RecipeTable.description],
            imageUrl = recipeRow[RecipeTable.image_url],
            imageUrlThumbnail = recipeRow[RecipeTable.image_url_thumbnail],
            prepTimeMinutes = recipeRow[RecipeTable.prep_time_minutes],
            cookTimeMinutes = recipeRow[RecipeTable.cook_time_minutes],
            servings = recipeRow[RecipeTable.servings],
            creatorId = recipeRow[RecipeTable.creator_id].toString(),
            recipeExternalUrl = recipeRow[RecipeTable.recipe_external_url],
            privacy = recipeRow[RecipeTable.privacy],
            updatedAt = recipeRow[RecipeTable.updated_at],
            deletedAt = recipeRow[RecipeTable.deleted_at],
            steps = steps,
            ingredients = ingredients,
            tagIds = tagIds,
            labelIds = labelIds
        )

        return SyncRecipeRecord(
            recipe = recipe,
            serverUpdatedAtMillis = recipeRow[RecipeTable.server_updated_at].toEpochMilliseconds()
        )
    }
}
