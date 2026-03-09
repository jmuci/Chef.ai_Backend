package com.tenmilelabs.infrastructure.database.repositoryImpl

import com.tenmilelabs.application.dto.SyncAllergen
import com.tenmilelabs.application.dto.SyncIngredient
import com.tenmilelabs.application.dto.SyncLabel
import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.application.dto.SyncRecipeIngredient
import com.tenmilelabs.application.dto.SyncRecipeStep
import com.tenmilelabs.application.dto.SyncReferenceData
import com.tenmilelabs.application.dto.SyncSourceClassification
import com.tenmilelabs.application.dto.SyncTag
import com.tenmilelabs.application.dto.SyncUser
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
                it[creator_id] = EntityID(UUID.fromString(recipe.creatorId), UserTable)
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
                it[creator_id] = EntityID(UUID.fromString(recipe.creatorId), UserTable)
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
                    ((RecipeTable.creator_id eq EntityID(userId, UserTable)) or (RecipeTable.privacy eq "PUBLIC"))
            }
            .orderBy(RecipeTable.server_updated_at to SortOrder.ASC)
            .limit(limit)
            .map(::toSyncRecipeRecord)
    }

    override suspend fun ingredientExists(uuid: UUID): Boolean = suspendTransaction {
        IngredientTable.selectAll().where { IngredientTable.id eq uuid }.limit(1).any()
    }

    override suspend fun collectReferenceData(
        ingredientIds: Set<UUID>,
        tagIds: Set<UUID>,
        labelIds: Set<UUID>,
        sinceMillis: Long?
    ): SyncReferenceData = suspendTransaction {
        val sinceInstant = sinceMillis?.let { Instant.fromEpochMilliseconds(it) }

        val ingredients = queryIngredients(ingredientIds, sinceInstant)

        // Derive transitive dependencies from the returned ingredients so that
        // delta ingredients always bring their own allergen / source-classification rows.
        val allergenIds = ingredients.mapNotNull { it.allergenId }.map { UUID.fromString(it) }.toSet()
        val sourceClassificationIds = ingredients.mapNotNull { it.sourcePrimaryId }.map { UUID.fromString(it) }.toSet()

        SyncReferenceData(
            ingredients = ingredients,
            allergens = queryAllergens(allergenIds, sinceInstant),
            sourceClassifications = querySourceClassifications(sourceClassificationIds, sinceInstant),
            tags = queryTags(tagIds, sinceInstant),
            labels = queryLabels(labelIds, sinceInstant)
        )
    }

    override suspend fun collectCreators(
        creatorIds: Set<UUID>,
        sinceMillis: Long?
    ): List<SyncUser> = suspendTransaction {
        val sinceInstant = sinceMillis?.let { Instant.fromEpochMilliseconds(it) }
        queryCreators(creatorIds, sinceInstant)
    }

    // ── Private query helpers ──────────────────────────────────────────────────
    // Each helper applies the union pattern inside the where{} lambda where the
    // Exposed SqlExpressionBuilder operators (greater, inList, or) are in scope.
    // When sinceInstant is null only the gap (id IN ids) clause is used.

    private fun queryIngredients( ids: Set<UUID>, sinceInstant: Instant?,): List<SyncIngredient> {
        if (sinceInstant == null && ids.isEmpty()) return emptyList()
        val entityIds = ids.map { EntityID(it, IngredientTable) }
        return IngredientTable.selectAll().where {
            when {
                sinceInstant != null && entityIds.isNotEmpty() ->
                    (IngredientTable.server_updated_at greater sinceInstant) or (IngredientTable.id inList entityIds)
                sinceInstant != null ->
                    IngredientTable.server_updated_at greater sinceInstant
                else ->
                    IngredientTable.id inList entityIds
            }
        }.map { row ->
            SyncIngredient(
                uuid = row[IngredientTable.id].value.toString(),
                displayName = row[IngredientTable.display_name],
                allergenId = row[IngredientTable.allergen_id]?.value?.toString(),
                sourcePrimaryId = row[IngredientTable.source_primary_id]?.toString(),
                updatedAt = row[IngredientTable.updated_at],
                deletedAt = row[IngredientTable.deleted_at]
            )
        }
    }

    private fun queryAllergens(ids: Set<UUID>, sinceInstant: Instant?): List<SyncAllergen> {
        if (sinceInstant == null && ids.isEmpty()) return emptyList()
        val entityIds = ids.map { EntityID(it, AllergenTable) }
        return AllergenTable.selectAll().where {
            when {
                sinceInstant != null && entityIds.isNotEmpty() ->
                    (AllergenTable.server_updated_at greater sinceInstant) or (AllergenTable.id inList entityIds)
                sinceInstant != null ->
                    AllergenTable.server_updated_at greater sinceInstant
                else ->
                    AllergenTable.id inList entityIds
            }
        }.map { row ->
            SyncAllergen(
                uuid = row[AllergenTable.id].value.toString(),
                displayName = row[AllergenTable.display_name],
                updatedAt = row[AllergenTable.updated_at],
                deletedAt = row[AllergenTable.deleted_at]
            )
        }
    }

    private fun querySourceClassifications(ids: Set<UUID>, sinceInstant: Instant?): List<SyncSourceClassification> {
        if (sinceInstant == null && ids.isEmpty()) return emptyList()
        val entityIds = ids.map { EntityID(it, SourceClassificationTable) }
        return SourceClassificationTable.selectAll().where {
            when {
                sinceInstant != null && entityIds.isNotEmpty() ->
                    (SourceClassificationTable.server_updated_at greater sinceInstant) or
                        (SourceClassificationTable.id inList entityIds)
                sinceInstant != null ->
                    SourceClassificationTable.server_updated_at greater sinceInstant
                else ->
                    SourceClassificationTable.id inList entityIds
            }
        }.map { row ->
            SyncSourceClassification(
                uuid = row[SourceClassificationTable.id].value.toString(),
                category = row[SourceClassificationTable.category],
                subcategory = row[SourceClassificationTable.subcategory],
                updatedAt = row[SourceClassificationTable.updated_at],
                deletedAt = row[SourceClassificationTable.deleted_at]
            )
        }
    }

    private fun queryTags(ids: Set<UUID>, sinceInstant: Instant?): List<SyncTag> {
        if (sinceInstant == null && ids.isEmpty()) return emptyList()
        val entityIds = ids.map { EntityID(it, TagTable) }
        return TagTable.selectAll().where {
            when {
                sinceInstant != null && entityIds.isNotEmpty() ->
                    (TagTable.server_updated_at greater sinceInstant) or (TagTable.id inList entityIds)
                sinceInstant != null ->
                    TagTable.server_updated_at greater sinceInstant
                else ->
                    TagTable.id inList entityIds
            }
        }.map { row ->
            SyncTag(
                uuid = row[TagTable.id].value.toString(),
                displayName = row[TagTable.display_name],
                updatedAt = row[TagTable.updated_at],
                deletedAt = row[TagTable.deleted_at]
            )
        }
    }

    private fun queryLabels(ids: Set<UUID>, sinceInstant: Instant?): List<SyncLabel> {
        if (sinceInstant == null && ids.isEmpty()) return emptyList()
        val entityIds = ids.map { EntityID(it, LabelTable) }
        return LabelTable.selectAll().where {
            when {
                sinceInstant != null && entityIds.isNotEmpty() ->
                    (LabelTable.server_updated_at greater sinceInstant) or (LabelTable.id inList entityIds)
                sinceInstant != null ->
                    LabelTable.server_updated_at greater sinceInstant
                else ->
                    LabelTable.id inList entityIds
            }
        }.map { row ->
            SyncLabel(
                uuid = row[LabelTable.id].value.toString(),
                displayName = row[LabelTable.display_name],
                updatedAt = row[LabelTable.updated_at],
                deletedAt = row[LabelTable.deleted_at]
            )
        }
    }

    private fun queryCreators(ids: Set<UUID>, sinceInstant: Instant?): List<SyncUser> {
        if (sinceInstant == null && ids.isEmpty()) return emptyList()
        val entityIds = ids.map { EntityID(it, UserTable) }
        return UserTable.selectAll().where {
            when {
                sinceInstant != null && entityIds.isNotEmpty() ->
                    (UserTable.updated_at greater sinceInstant) or (UserTable.id inList entityIds)
                sinceInstant != null ->
                    UserTable.updated_at greater sinceInstant
                else ->
                    UserTable.id inList entityIds
            }
        }.map { row ->
            val email = row.getOrNull(UserTable.email).orEmpty()
            val displayName = row.getOrNull(UserTable.display_name)
                ?.takeIf { it.isNotBlank() }
                ?: row.getOrNull(UserTable.user_name)?.takeIf { it.isNotBlank() }
                ?: email
            SyncUser(
                uuid = row[UserTable.id].value.toString(),
                displayName = displayName,
                email = email,
                avatarUrl = row.getOrNull(UserTable.avatar_url).orEmpty(),
                updatedAt = row[UserTable.updated_at].toEpochMilliseconds(),
                deletedAt = null
            )
        }
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
            creatorId = recipeRow[RecipeTable.creator_id].value.toString(),
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
