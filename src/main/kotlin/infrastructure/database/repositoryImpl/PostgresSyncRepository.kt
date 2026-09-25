package com.tenmilelabs.infrastructure.database.repositoryImpl

import com.tenmilelabs.application.dto.*
import com.tenmilelabs.domain.repository.RecipeRankingMetadata
import com.tenmilelabs.domain.repository.SyncGroceryItemRecord
import com.tenmilelabs.domain.repository.SyncMealPlanRecord
import com.tenmilelabs.domain.repository.SyncRecipeRecord
import com.tenmilelabs.domain.repository.RecipeUpsertOutcome
import com.tenmilelabs.domain.repository.SyncRepository
import com.tenmilelabs.domain.service.MealPlanGenerationService
import com.tenmilelabs.infrastructure.database.mappers.suspendTransaction
import com.tenmilelabs.infrastructure.database.tables.*
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
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

        loadRecipeAggregates(listOf(recipeRow)).single()
    }

    override suspend fun getRecipes(uuids: Set<UUID>): List<SyncRecipeRecord> = suspendTransaction {
        if (uuids.isEmpty()) return@suspendTransaction emptyList()
        val entityIds = uuids.map { EntityID(it, RecipeTable) }
        loadRecipeAggregates(RecipeTable.selectAll().where { RecipeTable.id inList entityIds }.toList())
    }

    /**
     * Hydrates [recipeRows] into full aggregates with one query per child table for the whole
     * batch (not per recipe), preserving [recipeRows]' order. Must run inside a transaction.
     */
    private fun loadRecipeAggregates(recipeRows: List<ResultRow>): List<SyncRecipeRecord> {
        if (recipeRows.isEmpty()) return emptyList()
        val entityIds = recipeRows.map { it[RecipeTable.id] }

        val stepsByRecipe = RecipeStepTable.selectAll()
            .where { (RecipeStepTable.recipe_id inList entityIds) and RecipeStepTable.deleted_at.isNull() }
            .orderBy(RecipeStepTable.order_index to SortOrder.ASC)
            .groupBy({ it[RecipeStepTable.recipe_id].value }) { row ->
                SyncRecipeStep(
                    uuid = row[RecipeStepTable.id].value.toString(),
                    orderIndex = row[RecipeStepTable.order_index],
                    instruction = row[RecipeStepTable.instruction]
                )
            }
        val ingredientsByRecipe = RecipeIngredientTable.selectAll()
            .where { (RecipeIngredientTable.recipeId inList entityIds) and RecipeIngredientTable.deletedAt.isNull() }
            .groupBy({ it[RecipeIngredientTable.recipeId].value }) { row ->
                SyncRecipeIngredient(
                    ingredientId = row[RecipeIngredientTable.ingredientId].value.toString(),
                    quantity = row[RecipeIngredientTable.quantity],
                    unit = row[RecipeIngredientTable.unit]
                )
            }
        val tagIdsByRecipe = RecipeTagTable.selectAll()
            .where { (RecipeTagTable.recipeId inList entityIds) and RecipeTagTable.deletedAt.isNull() }
            .groupBy({ it[RecipeTagTable.recipeId].value }) { it[RecipeTagTable.tagId].value.toString() }
        val labelIdsByRecipe = RecipeLabelTable.selectAll()
            .where { (RecipeLabelTable.recipeId inList entityIds) and RecipeLabelTable.deletedAt.isNull() }
            .groupBy({ it[RecipeLabelTable.recipeId].value }) { it[RecipeLabelTable.labelId].value.toString() }

        return recipeRows.map { recipeRow ->
            val recipeId = recipeRow[RecipeTable.id].value
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
                steps = stepsByRecipe[recipeId].orEmpty(),
                ingredients = ingredientsByRecipe[recipeId].orEmpty(),
                tagIds = tagIdsByRecipe[recipeId].orEmpty(),
                labelIds = labelIdsByRecipe[recipeId].orEmpty(),
                imageBlobId = recipeRow[RecipeTable.image_blob_id]
            )
            SyncRecipeRecord(recipe = recipe, serverUpdatedAtMillis = recipeRow[RecipeTable.server_updated_at].toEpochMilliseconds())
        }
    }

    /**
     * Persists a full recipe aggregate using replace semantics for children.
     *
     * Existing child rows are deleted and re-inserted from the incoming payload
     * so the server snapshot matches the client aggregate atomically.
     */
    override suspend fun upsertRecipeAggregate(
        recipe: SyncRecipe,
        serverUpdatedAt: Instant
    ): RecipeUpsertOutcome = suspendTransaction {
        val recipeUuid = UUID.fromString(recipe.uuid)
        val recipeEntityId = EntityID(recipeUuid, RecipeTable)

        // Locked so a concurrent push of the same recipe blocks here rather than racing the
        // staleness re-check below - see upsertMealPlan, which this mirrors.
        val existingRow = RecipeTable
            .selectAll()
            .where { RecipeTable.id eq recipeUuid }
            .forUpdate()
            .firstOrNull()
        val exists = existingRow != null

        if (existingRow != null &&
            existingRow[RecipeTable.server_updated_at].toEpochMilliseconds() > recipe.updatedAt
        ) {
            return@suspendTransaction RecipeUpsertOutcome.ServerNewer
        }

        // Step ids are client-generated primary keys. One already owned by a different recipe
        // would fail the insert below with a constraint violation that aborts the whole push
        // request, so detect it up front and report it per-recipe instead.
        val stepEntityIds = recipe.steps.map { EntityID(UUID.fromString(it.uuid), RecipeStepTable) }
        if (stepEntityIds.isNotEmpty()) {
            val stepIdTaken = RecipeStepTable.selectAll()
                .where { (RecipeStepTable.id inList stepEntityIds) and (RecipeStepTable.recipe_id neq recipeEntityId) }
                .limit(1)
                .any()
            if (stepIdTaken) return@suspendTransaction RecipeUpsertOutcome.StepIdTaken
        }

        if (exists) {
            RecipeTable.update({ RecipeTable.id eq recipeUuid }) {
                it[title] = recipe.title
                it[description] = recipe.description
                it[image_url] = recipe.imageUrl
                it[image_url_thumbnail] = recipe.imageUrlThumbnail
                it[prep_time_minutes] = recipe.prepTimeMinutes
                it[cook_time_minutes] = recipe.cookTimeMinutes
                it[servings] = recipe.servings
                // creator_id is deliberately NOT written on the update path. Ownership is set once
                // at insert and is not a client-editable field: writing it here let a push payload
                // transfer someone else's recipe to the caller. SyncService rejects a push against
                // a row the caller doesn't own before reaching this point; this keeps the column
                // immutable even if some future caller misses that check.
                it[recipe_external_url] = recipe.recipeExternalUrl
                it[privacy] = recipe.privacy
                it[updated_at] = recipe.updatedAt
                it[deleted_at] = recipe.deletedAt
                it[RecipeTable.server_updated_at] = serverUpdatedAt
            }
        } else {
            try {
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
            } catch (ex: ExposedSQLException) {
                // A concurrent push (e.g. a client retry after a timeout) inserted this brand-new
                // uuid first - report a conflict rather than letting the violation fail the batch.
                if (ex.message?.contains("recipes_pkey", ignoreCase = true) == true) {
                    return@suspendTransaction RecipeUpsertOutcome.ServerNewer
                }
                throw ex
            }
        }

        // A recipe soft-deleted through /sync/push can be the last pointer to a blob — the
        // same reclamation check that runs on repoint/clear (§6) must run here too.
        val previousImageBlobId = existingRow?.get(RecipeTable.image_blob_id)
        if (recipe.deletedAt != null && previousImageBlobId != null) {
            applyImageDereferenceCheck(
                userId = UUID.fromString(recipe.creatorId),
                contentHash = previousImageBlobId,
                nowMillis = serverUpdatedAt.toEpochMilliseconds()
            )
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
        RecipeUpsertOutcome.Applied
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
            .toList()
            .let(::loadRecipeAggregates)
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

    override suspend fun collectCreators(creatorIds: Set<UUID>): List<SyncUser> = suspendTransaction {
        queryCreators(creatorIds)
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

    /** Gap-only by design — see [SyncRepository.collectCreators] for why users get no delta clause. */
    private fun queryCreators(ids: Set<UUID>): List<SyncUser> {
        if (ids.isEmpty()) return emptyList()
        val entityIds = ids.map { EntityID(it, UserTable) }
        return UserTable.selectAll().where { UserTable.id inList entityIds }.map { row ->
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

    override suspend fun isRecipeAccessibleBy(userId: UUID, recipeId: UUID): Boolean = suspendTransaction {
        RecipeTable.selectAll()
            .where {
                (RecipeTable.id eq recipeId) and
                    ((RecipeTable.creator_id eq EntityID(userId, UserTable)) or (RecipeTable.privacy eq "PUBLIC"))
            }
            .limit(1)
            .any()
    }

    override suspend fun accessibleRecipeIds(userId: UUID, recipeIds: Set<UUID>): Set<UUID> = suspendTransaction {
        if (recipeIds.isEmpty()) return@suspendTransaction emptySet()
        val entityIds = recipeIds.map { EntityID(it, RecipeTable) }
        val ownedOrPublic = RecipeTable.selectAll()
            .where {
                (RecipeTable.id inList entityIds) and
                    ((RecipeTable.creator_id eq EntityID(userId, UserTable)) or (RecipeTable.privacy eq "PUBLIC"))
            }
            .map { it[RecipeTable.id].value }
            .toSet()
        val householdId = resolveActiveHouseholdId(userId)
        val householdVisible = if (householdId != null) householdVisibleRecipeIds(householdId) else emptySet()
        ownedOrPublic + (householdVisible intersect recipeIds)
    }

    override suspend fun upsertBookmark(
        userId: UUID,
        recipeId: UUID,
        deletedAt: Instant?,
        serverUpdatedAt: Instant
    ): Unit = suspendTransaction {
        val existing = BookmarkedRecipeTable
            .selectAll()
            .where {
                (BookmarkedRecipeTable.user_id eq EntityID(userId, UserTable)) and
                    (BookmarkedRecipeTable.recipe_id eq EntityID(recipeId, RecipeTable))
            }
            .limit(1)
            .any()

        if (existing) {
            BookmarkedRecipeTable.update({
                (BookmarkedRecipeTable.user_id eq EntityID(userId, UserTable)) and
                    (BookmarkedRecipeTable.recipe_id eq EntityID(recipeId, RecipeTable))
            }) {
                it[BookmarkedRecipeTable.server_updated_at] = serverUpdatedAt
                it[BookmarkedRecipeTable.deleted_at] = deletedAt
            }
        } else {
            BookmarkedRecipeTable.insert {
                it[BookmarkedRecipeTable.user_id] = EntityID(userId, UserTable)
                it[BookmarkedRecipeTable.recipe_id] = EntityID(recipeId, RecipeTable)
                it[BookmarkedRecipeTable.server_updated_at] = serverUpdatedAt
                it[BookmarkedRecipeTable.deleted_at] = deletedAt
            }
        }
    }

    override suspend fun findDeltaBookmarks(userId: UUID, sinceMillis: Long): List<SyncBookmark> = suspendTransaction {
        val sinceInstant = Instant.fromEpochMilliseconds(sinceMillis)
        BookmarkedRecipeTable
            .selectAll()
            .where {
                (BookmarkedRecipeTable.user_id eq EntityID(userId, UserTable)) and
                    (BookmarkedRecipeTable.server_updated_at greater sinceInstant)
            }
            .map { row ->
                SyncBookmark(
                    userId = row[BookmarkedRecipeTable.user_id].value.toString(),
                    recipeId = row[BookmarkedRecipeTable.recipe_id].value.toString(),
                    updatedAt = row[BookmarkedRecipeTable.server_updated_at].toEpochMilliseconds(),
                    deletedAt = row[BookmarkedRecipeTable.deleted_at]?.toEpochMilliseconds()
                )
            }
    }

    // ── Meal Plans ────────────────────────────────────────────────────────────

    override suspend fun isActiveHouseholdMember(userId: UUID, householdId: UUID): Boolean = suspendTransaction {
        resolveActiveHouseholdId(userId) == householdId
    }

    override suspend fun getMealPlanForMember(uuid: UUID, userId: UUID): SyncMealPlanRecord? = suspendTransaction {
        val householdId = resolveActiveHouseholdId(userId)
        val planRow = MealPlanTable
            .selectAll()
            .where {
                (MealPlanTable.id eq uuid) and memberAccessClause(userId, householdId)
            }
            .firstOrNull() ?: return@suspendTransaction null
        toSyncMealPlanRecord(planRow)
    }

    override suspend fun mealPlanExists(uuid: UUID): Boolean = suspendTransaction {
        MealPlanTable.selectAll().where { MealPlanTable.id eq uuid }.limit(1).any()
    }

    override suspend fun upsertMealPlan(plan: SyncMealPlanDto, serverUpdatedAt: Instant): Boolean = suspendTransaction {
        val planUuid = UUID.fromString(plan.uuid)
        val planEntityId = EntityID(planUuid, MealPlanTable)

        // Locks the row (if it already exists) so a concurrent push for the same plan blocks here
        // instead of racing this staleness check — the caller's own `existing` read happened in a
        // separate, already-committed transaction and can be stale by the time this runs. Without
        // this lock two concurrent pushes could both see "no conflict" and the second would
        // silently overwrite the first with no conflict ever reported.
        val existingRow = MealPlanTable
            .selectAll()
            .where { MealPlanTable.id eq planUuid }
            .forUpdate()
            .firstOrNull()

        if (existingRow != null) {
            if (existingRow[MealPlanTable.server_updated_at].toEpochMilliseconds() > plan.updatedAt) {
                return@suspendTransaction false
            }
            // Owner and household are immutable via sync — only HouseholdService's lifecycle
            // methods touch them (see MealPlanTable.household_id's KDoc).
            MealPlanTable.update({ MealPlanTable.id eq planUuid }) {
                it[name] = plan.name
                it[status] = plan.status
                it[preferences] = plan.preferencesJson
                it[updated_at] = plan.updatedAt
                it[deleted_at] = plan.deletedAt
                it[MealPlanTable.server_updated_at] = serverUpdatedAt
            }
        } else {
            try {
                MealPlanTable.insert {
                    it[id] = planEntityId
                    it[user_id] = EntityID(UUID.fromString(plan.ownerId), UserTable)
                    it[household_id] = plan.householdId?.let { id -> EntityID(UUID.fromString(id), HouseholdTable) }
                    it[name] = plan.name
                    it[status] = plan.status
                    it[preferences] = plan.preferencesJson
                    it[created_at] = plan.createdAt
                    it[updated_at] = plan.updatedAt
                    it[deleted_at] = plan.deletedAt
                    it[MealPlanTable.server_updated_at] = serverUpdatedAt
                }
            } catch (ex: ExposedSQLException) {
                // A concurrent push for the same brand-new plan id committed its INSERT first —
                // report this one as a conflict instead of letting the constraint violation escape.
                if (ex.message?.contains("meal_plans_pkey", ignoreCase = true) == true) {
                    return@suspendTransaction false
                }
                throw ex
            }
        }

        // Replace days atomically
        MealPlanDayTable.deleteWhere { MealPlanDayTable.meal_plan_id eq planEntityId }
        plan.days.forEach { day ->
            MealPlanDayTable.insert {
                it[MealPlanDayTable.id] = EntityID(UUID.fromString(day.uuid), MealPlanDayTable)
                it[meal_plan_id] = planEntityId
                it[day_index] = day.dayIndex
                it[dinner_recipe_id] = day.dinnerRecipeId?.let { id -> EntityID(UUID.fromString(id), RecipeTable) }
                it[lunch_recipe_id] = day.lunchRecipeId?.let { id -> EntityID(UUID.fromString(id), RecipeTable) }
            }
        }
        true
    }

    override suspend fun findDeltaMealPlans(userId: UUID, sinceMillis: Long): List<SyncMealPlanRecord> =
        suspendTransaction {
            val sinceInstant = Instant.fromEpochMilliseconds(sinceMillis)
            val householdId = resolveActiveHouseholdId(userId)

            val normal = MealPlanTable
                .selectAll()
                .where {
                    (MealPlanTable.server_updated_at greater sinceInstant) and memberAccessClause(userId, householdId)
                }
                .map(::toSyncMealPlanRecord)

            val tombstoneTimestamps = tombstoneTimestampsByPlanId(userId, sinceInstant, householdId)
            val tombstones = if (tombstoneTimestamps.isEmpty()) {
                emptyList()
            } else {
                MealPlanTable
                    .selectAll()
                    .where { MealPlanTable.id inList tombstoneTimestamps.keys.map { EntityID(it, MealPlanTable) } }
                    .map { row -> toSyncMealPlanRecord(row).withSyntheticDeletedAt(tombstoneTimestamps.getValue(row[MealPlanTable.id].value)) }
            }

            (normal + tombstones)
                .distinctBy { it.plan.uuid }
                .sortedBy { it.serverUpdatedAtMillis }
        }

    override suspend fun getGroceryListItem(mealPlanId: UUID, itemKey: String): SyncGroceryItemRecord? =
        suspendTransaction {
            GroceryListItemCheckTable
                .selectAll()
                .where {
                    (GroceryListItemCheckTable.meal_plan_id eq EntityID(mealPlanId, MealPlanTable)) and
                        (GroceryListItemCheckTable.item_key eq itemKey)
                }
                .firstOrNull()
                ?.let(::toSyncGroceryItemRecord)
        }

    override suspend fun upsertGroceryListItem(item: SyncGroceryListItem, serverUpdatedAt: Instant): Unit =
        suspendTransaction {
            val planEntityId = EntityID(UUID.fromString(item.mealPlanId), MealPlanTable)
            val checkedByEntityId = item.checkedBy?.let { EntityID(UUID.fromString(it), UserTable) }

            val exists = GroceryListItemCheckTable
                .selectAll()
                .where {
                    (GroceryListItemCheckTable.meal_plan_id eq planEntityId) and
                        (GroceryListItemCheckTable.item_key eq item.itemKey)
                }
                .limit(1)
                .any()

            if (exists) {
                GroceryListItemCheckTable.update({
                    (GroceryListItemCheckTable.meal_plan_id eq planEntityId) and
                        (GroceryListItemCheckTable.item_key eq item.itemKey)
                }) {
                    it[checked] = item.checked
                    it[checked_by] = checkedByEntityId
                    it[updated_at] = item.updatedAt
                    it[deleted_at] = item.deletedAt
                    it[GroceryListItemCheckTable.server_updated_at] = serverUpdatedAt
                }
            } else {
                GroceryListItemCheckTable.insert {
                    it[meal_plan_id] = planEntityId
                    it[item_key] = item.itemKey
                    it[checked] = item.checked
                    it[checked_by] = checkedByEntityId
                    it[updated_at] = item.updatedAt
                    it[deleted_at] = item.deletedAt
                    it[GroceryListItemCheckTable.server_updated_at] = serverUpdatedAt
                }
            }
        }

    override suspend fun findDeltaGroceryListItems(userId: UUID, sinceMillis: Long): List<SyncGroceryItemRecord> =
        suspendTransaction {
            val sinceInstant = Instant.fromEpochMilliseconds(sinceMillis)
            val householdId = resolveActiveHouseholdId(userId)

            val accessiblePlanIds = MealPlanTable
                .selectAll()
                .where { memberAccessClause(userId, householdId) }
                .map { it[MealPlanTable.id] }

            val normal = if (accessiblePlanIds.isEmpty()) {
                emptyList()
            } else {
                GroceryListItemCheckTable
                    .selectAll()
                    .where {
                        (GroceryListItemCheckTable.server_updated_at greater sinceInstant) and
                            (GroceryListItemCheckTable.meal_plan_id inList accessiblePlanIds)
                    }
                    .map(::toSyncGroceryItemRecord)
            }

            val tombstoneTimestamps = tombstoneTimestampsByPlanId(userId, sinceInstant, householdId)
            val tombstones = if (tombstoneTimestamps.isEmpty()) {
                emptyList()
            } else {
                GroceryListItemCheckTable
                    .selectAll()
                    .where { GroceryListItemCheckTable.meal_plan_id inList tombstoneTimestamps.keys.map { EntityID(it, MealPlanTable) } }
                    .map { row ->
                        val planId = row[GroceryListItemCheckTable.meal_plan_id].value
                        toSyncGroceryItemRecord(row).withSyntheticDeletedAt(tombstoneTimestamps.getValue(planId))
                    }
            }

            (normal + tombstones)
                .distinctBy { it.item.mealPlanId to it.item.itemKey }
                .sortedBy { it.serverUpdatedAtMillis }
        }

    /**
     * Plan ids [findDeltaMealPlans]/[findDeltaGroceryListItems] must send [userId] a synthetic
     * per-caller removal tombstone for, mapped to the epoch-millis timestamp the tombstone should
     * carry. Two independent sources, both real access-loss events the plan's own `deleted_at`
     * never reflects (everyone else with real access still sees it as-is):
     *
     * - [userId] was themselves removed from a household after [sinceInstant] — every plan still
     *   shared with that household (backend prompt §6.2).
     * - A plan shared with [userId]'s *current* active household ([activeHouseholdId]) was
     *   detached from it after [sinceInstant] (its owner left) — [userId] wasn't the one who left,
     *   so nothing else tells their client the plan they'd already cached is no longer shared. See
     *   [com.tenmilelabs.infrastructure.database.tables.MealPlanTable]'s KDoc on
     *   `former_household_id`/`household_detached_at`.
     *
     * A plan matching both sources keeps whichever timestamp the map-building order lands on —
     * both are real access-loss events for [userId], so which exact instant the tombstone carries
     * doesn't change client behavior (the row disappears either way).
     */
    private fun tombstoneTimestampsByPlanId(
        userId: UUID,
        sinceInstant: Instant,
        activeHouseholdId: UUID?
    ): Map<UUID, Long> {
        val fromRemoval = HouseholdMemberTable
            .selectAll()
            .where {
                (HouseholdMemberTable.user_id eq EntityID(userId, UserTable)) and
                    (HouseholdMemberTable.status eq "REMOVED") and
                    (HouseholdMemberTable.server_removed_at greater sinceInstant)
            }
            .mapNotNull { row ->
                val removedHouseholdId = row[HouseholdMemberTable.household_id].value
                val removedAtMillis = row[HouseholdMemberTable.server_removed_at]?.toEpochMilliseconds()
                    ?: return@mapNotNull null
                removedHouseholdId to removedAtMillis
            }
            .flatMap { (removedHouseholdId, removedAtMillis) ->
                val removedHousehold = EntityID(removedHouseholdId, HouseholdTable)
                MealPlanTable
                    .selectAll()
                    .where {
                        // former_household_id too: dissolveHousehold detaches every plan in the same
                        // transaction that removes the members, so a co-member's plan the caller had
                        // cached no longer matches on household_id. The caller's own plans are never
                        // tombstoned — they keep owner access to those regardless of households.
                        ((MealPlanTable.household_id eq removedHousehold) or
                            (MealPlanTable.former_household_id eq removedHousehold)) and
                            (MealPlanTable.user_id neq EntityID(userId, UserTable))
                    }
                    .map { it[MealPlanTable.id].value to removedAtMillis }
            }

        val fromDetachment = if (activeHouseholdId == null) {
            emptyList()
        } else {
            MealPlanTable
                .selectAll()
                .where {
                    // Excludes the caller's own plans: after leaving and rejoining the same
                    // household before pulling, their own detached plans match here but are still
                    // theirs.
                    (MealPlanTable.former_household_id eq EntityID(activeHouseholdId, HouseholdTable)) and
                        (MealPlanTable.household_detached_at greater sinceInstant) and
                        (MealPlanTable.user_id neq EntityID(userId, UserTable))
                }
                .mapNotNull { row ->
                    val detachedAtMillis = row[MealPlanTable.household_detached_at]?.toEpochMilliseconds()
                        ?: return@mapNotNull null
                    row[MealPlanTable.id].value to detachedAtMillis
                }
        }

        return (fromRemoval + fromDetachment).toMap()
    }

    override suspend fun findHouseholdVisibleRecipeIds(userId: UUID): Set<UUID> = suspendTransaction {
        val householdId = resolveActiveHouseholdId(userId) ?: return@suspendTransaction emptySet()
        householdVisibleRecipeIds(householdId)
    }

    override suspend fun isRecipeHouseholdVisible(userId: UUID, recipeId: UUID): Boolean = suspendTransaction {
        val householdId = resolveActiveHouseholdId(userId) ?: return@suspendTransaction false
        recipeId in householdVisibleRecipeIds(householdId)
    }

    /** `user_id = :userId OR household_id = :householdId` — [householdId] may be null (no active household). */
    private fun memberAccessClause(userId: UUID, householdId: UUID?): Op<Boolean> {
        val ownedByCaller = MealPlanTable.user_id eq EntityID(userId, UserTable)
        return if (householdId != null) {
            ownedByCaller or (MealPlanTable.household_id eq EntityID(householdId, HouseholdTable))
        } else {
            ownedByCaller
        }
    }

    /**
     * The household-sharing gap clause (backend prompt §6.3): dinner/lunch recipe ids referenced
     * by a day in a non-deleted plan under [householdId].
     */
    private fun householdVisibleRecipeIds(householdId: UUID): Set<UUID> {
        val planIds = MealPlanTable
            .selectAll()
            .where {
                (MealPlanTable.household_id eq EntityID(householdId, HouseholdTable)) and
                    MealPlanTable.deleted_at.isNull()
            }
            .map { it[MealPlanTable.id] }
        if (planIds.isEmpty()) return emptySet()

        return MealPlanDayTable
            .selectAll()
            .where { MealPlanDayTable.meal_plan_id inList planIds }
            .flatMap { row ->
                listOfNotNull(
                    row[MealPlanDayTable.dinner_recipe_id]?.value,
                    row[MealPlanDayTable.lunch_recipe_id]?.value
                )
            }
            .toSet()
    }

    /**
     * The caller's single active household, or null. Membership is single-valued (see the
     * partial unique index on `household_members`), so this is always at most one row. Private
     * and re-resolved per call rather than shared with [com.tenmilelabs.infrastructure.database.
     * repositoryImpl.PostgresHouseholdRepository] — this class already reaches into other tables
     * directly (see [findCandidateRecipeIds]), same pattern.
     */
    private fun resolveActiveHouseholdId(userId: UUID): UUID? =
        HouseholdMemberTable
            .selectAll()
            .where {
                (HouseholdMemberTable.user_id eq EntityID(userId, UserTable)) and
                    (HouseholdMemberTable.status eq "ACTIVE")
            }
            .firstOrNull()
            ?.get(HouseholdMemberTable.household_id)
            ?.value

    override suspend fun updateMealPlanStatus(planId: UUID, status: String, serverUpdatedAt: Instant): Unit =
        suspendTransaction {
            MealPlanTable.update({ MealPlanTable.id eq planId }) {
                it[MealPlanTable.status] = status
                it[MealPlanTable.server_updated_at] = serverUpdatedAt
            }
        }

    override suspend fun replaceMealPlanDays(planId: UUID, days: List<SyncMealPlanDayDto>): Unit =
        suspendTransaction {
            val planEntityId = EntityID(planId, MealPlanTable)
            MealPlanDayTable.deleteWhere { MealPlanDayTable.meal_plan_id eq planEntityId }
            days.forEach { day ->
                MealPlanDayTable.insert {
                    it[MealPlanDayTable.id] = EntityID(UUID.fromString(day.uuid), MealPlanDayTable)
                    it[meal_plan_id] = planEntityId
                    it[day_index] = day.dayIndex
                    it[dinner_recipe_id] = day.dinnerRecipeId?.let { id -> EntityID(UUID.fromString(id), RecipeTable) }
                    it[lunch_recipe_id] = day.lunchRecipeId?.let { id -> EntityID(UUID.fromString(id), RecipeTable) }
                }
            }
        }

    override suspend fun findCandidateRecipeIds(
        userId: UUID?,
        recipeSource: String,
        dietaryRestrictionTags: List<String>,
        maxPrepTimeMinutes: Int?
    ): List<UUID> = suspendTransaction {
        // Step 1: Resolve dietary restrictions against LABELS, not tags — the dietary vocabulary
        // (Vegan, Vegetarian, Gluten-Free, Dairy-Free, Keto, Paleo, Low Carb, ...) lives in
        // LabelTable. Tags are for cuisine/meal-type ("Spicy", "Breakfast", "Dinner"). Matching
        // against TagTable here previously meant no dietary restriction ever matched anything,
        // so the filter silently no-opped. Comparison is normalized (uppercase, spaces/hyphens ->
        // underscore) so client enum values like "GLUTEN_FREE" match the label "Gluten-Free".
        val restrictionLabelIds: List<EntityID<UUID>> = if (dietaryRestrictionTags.isEmpty()) {
            emptyList()
        } else {
            val requestedNormalized = dietaryRestrictionTags.map(::normalizeDietaryToken).toSet()
            LabelTable.selectAll()
                .where { LabelTable.deleted_at.isNull() }
                .filter { normalizeDietaryToken(it[LabelTable.display_name]) in requestedNormalized }
                .map { it[LabelTable.id] }
        }

        // Step 2: Build the base set of accessible recipe IDs per recipeSource
        val accessibleIds: Set<UUID> = when {
            recipeSource == "COLLECTION_ONLY" && userId != null -> {
                val bookmarkedIds = BookmarkedRecipeTable
                    .selectAll()
                    .where {
                        (BookmarkedRecipeTable.user_id eq EntityID(userId, UserTable)) and
                            BookmarkedRecipeTable.deleted_at.isNull()
                    }
                    .map { it[BookmarkedRecipeTable.recipe_id].value }
                    .toSet()
                // Nothing cleans up bookmarked_recipes when the bookmarked recipe is later
                // soft-deleted or its owner flips privacy away from PUBLIC, so a bookmark can
                // outlive the recipe's accessibility to this user. Re-check against the same
                // predicate /sync/pull uses (findDeltaRecipes) — otherwise a stale bookmark hands
                // out a candidate the client can never actually receive, leaving a permanently
                // unresolvable recipe reference in the generated plan.
                val accessibleBookmarked = if (bookmarkedIds.isEmpty()) {
                    emptySet()
                } else {
                    RecipeTable
                        .selectAll()
                        .where {
                            (RecipeTable.id inList bookmarkedIds.map { EntityID(it, RecipeTable) }) and
                                RecipeTable.deleted_at.isNull() and
                                ((RecipeTable.creator_id eq EntityID(userId, UserTable)) or (RecipeTable.privacy eq "PUBLIC"))
                        }
                        .map { it[RecipeTable.id].value }
                        .toSet()
                }
                val authored = RecipeTable
                    .selectAll()
                    .where {
                        (RecipeTable.creator_id eq EntityID(userId, UserTable)) and RecipeTable.deleted_at.isNull()
                    }
                    .map { it[RecipeTable.id].value }
                    .toSet()
                accessibleBookmarked + authored
            }
            // "INCLUDE_PUBLIC", or "COLLECTION_ONLY" with a null (anonymous) userId — a bookmark
            // collection is meaningless for a caller the server doesn't recognize, so it falls
            // through to the same public-only query as INCLUDE_PUBLIC.
            userId != null -> RecipeTable
                .selectAll()
                .where {
                    (RecipeTable.deleted_at.isNull()) and
                        ((RecipeTable.creator_id eq EntityID(userId, UserTable)) or (RecipeTable.privacy eq "PUBLIC"))
                }
                .map { it[RecipeTable.id].value }
                .toSet()
            else -> RecipeTable
                .selectAll()
                .where {
                    (RecipeTable.deleted_at.isNull()) and (RecipeTable.privacy eq "PUBLIC")
                }
                .map { it[RecipeTable.id].value }
                .toSet()
        }

        if (accessibleIds.isEmpty()) return@suspendTransaction emptyList()

        // Step 3: Intersect with dietary restriction label filters (AND logic — must have ALL labels)
        var candidateIds = accessibleIds
        for (labelEntityId in restrictionLabelIds) {
            val labeledIds = RecipeLabelTable
                .selectAll()
                .where { (RecipeLabelTable.labelId eq labelEntityId) and RecipeLabelTable.deletedAt.isNull() }
                .map { it[RecipeLabelTable.recipeId].value }
                .toSet()
            candidateIds = candidateIds.intersect(labeledIds)
            if (candidateIds.isEmpty()) return@suspendTransaction emptyList()
        }

        // Step 4: Apply time constraint
        if (maxPrepTimeMinutes == null) {
            candidateIds.toList()
        } else {
            val entityIds = candidateIds.map { EntityID(it, RecipeTable) }
            RecipeTable
                .selectAll()
                .where {
                    (RecipeTable.id inList entityIds) and
                        ((RecipeTable.prep_time_minutes + RecipeTable.cook_time_minutes) lessEq maxPrepTimeMinutes)
                }
                .map { it[RecipeTable.id].value }
        }
    }

    override suspend fun findRecipeRankingMetadata(recipeIds: Set<UUID>): Map<UUID, RecipeRankingMetadata> =
        suspendTransaction {
            if (recipeIds.isEmpty()) return@suspendTransaction emptyMap()
            val entityIds = recipeIds.map { EntityID(it, RecipeTable) }

            val servingsByRecipe = RecipeTable
                .selectAll()
                .where { RecipeTable.id inList entityIds }
                .associate { it[RecipeTable.id].value to it[RecipeTable.servings] }

            // Manual multi-step join (not a typed Exposed join): IngredientTable.source_primary_id
            // is a plain uuid() column, not a reference(), so it can't be compared directly against
            // SourceClassificationTable.id in the DSL.
            val ingredientRowsByRecipe = RecipeIngredientTable
                .selectAll()
                .where { (RecipeIngredientTable.recipeId inList entityIds) and RecipeIngredientTable.deletedAt.isNull() }
                .map { it[RecipeIngredientTable.recipeId].value to it[RecipeIngredientTable.ingredientId].value }
                .groupBy({ it.first }, { it.second })

            val allIngredientIds = ingredientRowsByRecipe.values.flatten().toSet()
            val categoryByIngredientId = if (allIngredientIds.isEmpty()) {
                emptyMap()
            } else {
                val ingredientEntityIds = allIngredientIds.map { EntityID(it, IngredientTable) }
                val sourceIdByIngredientId = IngredientTable
                    .selectAll()
                    .where { IngredientTable.id inList ingredientEntityIds }
                    .mapNotNull { row ->
                        row[IngredientTable.source_primary_id]?.let { row[IngredientTable.id].value to it }
                    }
                    .toMap()

                val sourceIds = sourceIdByIngredientId.values.toSet()
                val categoryBySourceId = if (sourceIds.isEmpty()) {
                    emptyMap()
                } else {
                    SourceClassificationTable
                        .selectAll()
                        .where { SourceClassificationTable.id inList sourceIds.map { EntityID(it, SourceClassificationTable) } }
                        .associate { it[SourceClassificationTable.id].value to it[SourceClassificationTable.category] }
                }

                sourceIdByIngredientId.mapNotNull { (ingredientId, sourceId) ->
                    categoryBySourceId[sourceId]?.let { ingredientId to it }
                }.toMap()
            }

            recipeIds.associateWith { recipeId ->
                val categories = ingredientRowsByRecipe[recipeId].orEmpty().mapNotNull { categoryByIngredientId[it] }
                val counts = categories.groupingBy { it }.eachCount()
                val maxCount = counts.values.maxOrNull()
                // Ties broken alphabetically for determinism.
                val dominantCategory = counts.filterValues { it == maxCount }.keys.minOrNull()

                RecipeRankingMetadata(
                    servings = servingsByRecipe[recipeId] ?: 0,
                    dominantCategory = dominantCategory
                )
            }
        }

    override suspend fun findRecentlyUsedRecipeIds(userId: UUID, excludePlanId: UUID, limit: Int): Set<UUID> =
        suspendTransaction {
            val recentPlanIds = MealPlanTable
                .selectAll()
                .where {
                    (MealPlanTable.user_id eq EntityID(userId, UserTable)) and
                        (MealPlanTable.status eq MealPlanGenerationService.STATUS_READY) and
                        (MealPlanTable.deleted_at.isNull()) and
                        (MealPlanTable.id neq EntityID(excludePlanId, MealPlanTable))
                }
                .orderBy(MealPlanTable.created_at to SortOrder.DESC)
                .limit(limit)
                .map { it[MealPlanTable.id] }

            if (recentPlanIds.isEmpty()) return@suspendTransaction emptySet()

            MealPlanDayTable
                .selectAll()
                .where { MealPlanDayTable.meal_plan_id inList recentPlanIds }
                .flatMap { row -> listOfNotNull(row[MealPlanDayTable.dinner_recipe_id]?.value, row[MealPlanDayTable.lunch_recipe_id]?.value) }
                .toSet()
        }

    /** Uppercases and folds spaces/hyphens to underscore, so "Gluten-Free" and "GLUTEN_FREE" compare equal. */
    private fun normalizeDietaryToken(raw: String): String =
        raw.trim().uppercase().replace(Regex("[\\s-]+"), "_")

    private fun toSyncMealPlanRecord(planRow: ResultRow): SyncMealPlanRecord {
        val planId = planRow[MealPlanTable.id].value
        val planEntityId = EntityID(planId, MealPlanTable)

        val days = MealPlanDayTable
            .selectAll()
            .where { MealPlanDayTable.meal_plan_id eq planEntityId }
            .orderBy(MealPlanDayTable.day_index to SortOrder.ASC)
            .map { dayRow ->
                SyncMealPlanDayDto(
                    uuid = dayRow[MealPlanDayTable.id].value.toString(),
                    dayIndex = dayRow[MealPlanDayTable.day_index],
                    dinnerRecipeId = dayRow[MealPlanDayTable.dinner_recipe_id]?.value?.toString(),
                    lunchRecipeId = dayRow[MealPlanDayTable.lunch_recipe_id]?.value?.toString()
                )
            }

        return SyncMealPlanRecord(
            plan = SyncMealPlanDto(
                uuid = planId.toString(),
                ownerId = planRow[MealPlanTable.user_id].value.toString(),
                householdId = planRow[MealPlanTable.household_id]?.value?.toString(),
                name = planRow[MealPlanTable.name],
                status = planRow[MealPlanTable.status],
                preferencesJson = planRow[MealPlanTable.preferences],
                createdAt = planRow[MealPlanTable.created_at],
                updatedAt = planRow[MealPlanTable.updated_at],
                deletedAt = planRow[MealPlanTable.deleted_at],
                days = days
            ),
            serverUpdatedAtMillis = planRow[MealPlanTable.server_updated_at].toEpochMilliseconds()
        )
    }

    /** Synthesizes a per-caller removal tombstone — the real row's `deleted_at` is untouched. */
    private fun SyncMealPlanRecord.withSyntheticDeletedAt(deletedAtMillis: Long): SyncMealPlanRecord =
        copy(plan = plan.copy(deletedAt = deletedAtMillis))

    /** Synthesizes a per-caller removal tombstone — the real row's `deleted_at` is untouched. */
    private fun SyncGroceryItemRecord.withSyntheticDeletedAt(deletedAtMillis: Long): SyncGroceryItemRecord =
        copy(item = item.copy(deletedAt = deletedAtMillis))

    private fun toSyncGroceryItemRecord(row: ResultRow): SyncGroceryItemRecord = SyncGroceryItemRecord(
        item = SyncGroceryListItem(
            mealPlanId = row[GroceryListItemCheckTable.meal_plan_id].value.toString(),
            itemKey = row[GroceryListItemCheckTable.item_key],
            checked = row[GroceryListItemCheckTable.checked],
            checkedBy = row[GroceryListItemCheckTable.checked_by]?.value?.toString(),
            updatedAt = row[GroceryListItemCheckTable.updated_at],
            deletedAt = row[GroceryListItemCheckTable.deleted_at]
        ),
        serverUpdatedAtMillis = row[GroceryListItemCheckTable.server_updated_at].toEpochMilliseconds()
    )
}
