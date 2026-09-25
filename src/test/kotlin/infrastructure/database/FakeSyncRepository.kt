package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.application.dto.SyncBookmark
import com.tenmilelabs.application.dto.SyncGroceryListItem
import com.tenmilelabs.application.dto.SyncIngredient
import com.tenmilelabs.application.dto.SyncLabel
import com.tenmilelabs.application.dto.SyncMealPlanDayDto
import com.tenmilelabs.application.dto.SyncMealPlanDto
import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.application.dto.SyncReferenceData
import com.tenmilelabs.application.dto.SyncTag
import com.tenmilelabs.application.dto.SyncUser
import com.tenmilelabs.domain.repository.RecipeRankingMetadata
import com.tenmilelabs.domain.repository.RecipeUpsertOutcome
import com.tenmilelabs.domain.repository.SyncGroceryItemRecord
import com.tenmilelabs.domain.repository.SyncMealPlanRecord
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
    // key: (userId, recipeId), value: bookmark with server-stamped updatedAt
    private val bookmarks = mutableMapOf<Pair<UUID, UUID>, SyncBookmark>()
    private val bookmarkServerTs = mutableMapOf<Pair<UUID, UUID>, Long>()
    // accessible recipe ids (by default, all seeded recipes are accessible)
    private val inaccessibleRecipes = mutableSetOf<UUID>()
    // meal plans: key=planId, value=record with server timestamp. Ownership/household come from
    // the stored plan's own ownerId/householdId fields, same as PostgresSyncRepository reads them
    // off the row rather than tracking them separately.
    private val mealPlans = mutableMapOf<UUID, SyncMealPlanRecord>()
    // key: userId, value: their current ACTIVE household - mirrors resolveActiveHouseholdId
    private val activeHousehold = mutableMapOf<UUID, UUID>()
    // key: userId, value: (householdId, serverRemovedAtMillis) of their most recent removal -
    // mirrors the household_members query findDeltaMealPlans's tombstone branch runs
    private val removedHouseholdMembership = mutableMapOf<UUID, Pair<UUID, Long>>()
    // key: planId, value: (formerHouseholdId, detachedAtMillis) - mirrors meal_plans.former_household_id
    // / household_detached_at, set when a plan's owner leaves the household it was shared with
    private val planDetachedFromHousehold = mutableMapOf<UUID, Pair<UUID, Long>>()
    // key: (mealPlanId, itemKey), value: record with server timestamp
    private val groceryItems = mutableMapOf<Pair<UUID, String>, SyncGroceryItemRecord>()
    // candidate recipe IDs for generation (injectable per-test)
    private val candidateRecipeIds = mutableListOf<UUID>()
    private val rankingMetadataByRecipe = mutableMapOf<UUID, RecipeRankingMetadata>()
    private val recentlyUsedElsewhereIds = mutableSetOf<UUID>()

    fun seedRankingMetadata(recipeId: UUID, servings: Int = 2, dominantCategory: String? = null) {
        rankingMetadataByRecipe[recipeId] = RecipeRankingMetadata(servings, dominantCategory)
    }

    fun seedRecentlyUsedElsewhere(recipeId: UUID) {
        recentlyUsedElsewhereIds += recipeId
    }

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
        recipes[UUID.fromString(recipe.uuid)] = SyncRecipeRecord(
            recipe = recipe,
            serverUpdatedAtMillis = serverUpdatedAtMillis
        )
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
        return uuid
    }

    fun makeRecipeInaccessible(recipeId: UUID) {
        inaccessibleRecipes += recipeId
    }

    fun seedMealPlan(plan: SyncMealPlanDto, serverUpdatedAtMillis: Long = 0L): UUID {
        val uuid = UUID.fromString(plan.uuid)
        mealPlans[uuid] = SyncMealPlanRecord(plan = plan, serverUpdatedAtMillis = serverUpdatedAtMillis)
        return uuid
    }

    fun seedGroceryItem(item: SyncGroceryListItem, serverUpdatedAtMillis: Long = 0L) {
        val key = UUID.fromString(item.mealPlanId) to item.itemKey
        groceryItems[key] = SyncGroceryItemRecord(item = item, serverUpdatedAtMillis = serverUpdatedAtMillis)
    }

    /** Test helper — makes [userId] appear as an ACTIVE member of [householdId]. */
    fun seedActiveHousehold(userId: UUID, householdId: UUID) {
        activeHousehold[userId] = householdId
    }

    /** Test helper — simulates [userId] having been removed from [householdId] at [serverRemovedAtMillis]. */
    fun seedRemovedFromHousehold(userId: UUID, householdId: UUID, serverRemovedAtMillis: Long) {
        removedHouseholdMembership[userId] = householdId to serverRemovedAtMillis
    }

    /**
     * Test helper — simulates [planId] having been detached from [householdId] (its owner left) at
     * [detachedAtMillis], mirroring `PostgresHouseholdRepository.detachPlansOwnedBy` stamping
     * `former_household_id`/`household_detached_at`. Used to exercise the tombstone a still-ACTIVE
     * member of [householdId] (not the one who left) should receive on their next pull.
     */
    fun seedPlanDetachedFromHousehold(planId: UUID, householdId: UUID, detachedAtMillis: Long) {
        planDetachedFromHousehold[planId] = householdId to detachedAtMillis
    }

    fun seedCandidateRecipe(uuid: UUID = UUID.randomUUID()): UUID {
        candidateRecipeIds += uuid
        return uuid
    }

    /**
     * Test-only mirror of what the image-upload endpoints do directly to the shared `recipes`
     * table in production (see PostgresImageBlobRepository.setRecipeImageBlobId). FakeSyncRepository
     * and FakeImageBlobRepository are independent in-memory stores, unlike the real
     * Postgres-backed repositories, so a test exercising both needs to call this explicitly
     * after a successful upload/clear for a pull to see the change.
     */
    fun setImageBlobId(recipeId: UUID, imageBlobId: String?) {
        val existing = recipes[recipeId] ?: return
        recipes[recipeId] = existing.copy(recipe = existing.recipe.copy(imageBlobId = imageBlobId))
    }

    override suspend fun getRecipe(uuid: UUID): SyncRecipeRecord? = recipes[uuid]

    override suspend fun getRecipes(uuids: Set<UUID>): List<SyncRecipeRecord> = uuids.mapNotNull { recipes[it] }

    override suspend fun upsertRecipeAggregate(recipe: SyncRecipe, serverUpdatedAt: Instant): RecipeUpsertOutcome {
        val existingRecord = recipes[UUID.fromString(recipe.uuid)]
        // Mirrors PostgresSyncRepository's write-time re-validation.
        if (existingRecord != null && existingRecord.serverUpdatedAtMillis > recipe.updatedAt) {
            return RecipeUpsertOutcome.ServerNewer
        }
        val incomingStepIds = recipe.steps.map { it.uuid }.toSet()
        val stepIdTaken = recipes.values.any { other ->
            other.recipe.uuid != recipe.uuid && other.recipe.steps.any { it.uuid in incomingStepIds }
        }
        if (stepIdTaken) return RecipeUpsertOutcome.StepIdTaken

        // Mirrors PostgresSyncRepository: imageBlobId is set exclusively by the image-upload
        // endpoints, never by a push payload — preserve whatever the server already had.
        val existing = existingRecord?.recipe
        recipes[UUID.fromString(recipe.uuid)] = SyncRecipeRecord(
            recipe = recipe.copy(
                imageBlobId = existing?.imageBlobId,
                // Also mirrors PostgresSyncRepository: creator_id is written at insert only, so an
                // update can never transfer ownership. Keeping the fake honest here is what lets
                // the takeover regression test below actually exercise the production rule.
                creatorId = existing?.creatorId ?: recipe.creatorId
            ),
            serverUpdatedAtMillis = serverUpdatedAt.toEpochMilliseconds()
        )
        return RecipeUpsertOutcome.Applied
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
        fun <V> Map<UUID, V>.unionFilter(ids: Set<UUID>, serverTs: Map<UUID, Long>): List<V> =
            filter { (uuid, _) ->
                (sinceMillis != null && (serverTs[uuid] ?: 0L) > sinceMillis) || uuid in ids
            }.values.toList()

        return SyncReferenceData(
            ingredients = ingredients.unionFilter(ingredientIds, ingredientServerTs),
            allergens = emptyList(),
            sourceClassifications = emptyList(),
            tags = tags.unionFilter(tagIds, tagServerTs),
            labels = labels.unionFilter(labelIds, labelServerTs)
        )
    }

    override suspend fun existingTagIds(ids: Set<UUID>): Set<UUID> = ids.intersect(tags.keys)

    override suspend fun existingLabelIds(ids: Set<UUID>): Set<UUID> = ids.intersect(labels.keys)

    override suspend fun collectCreators(creatorIds: Set<UUID>): List<SyncUser> =
        users.filterKeys { it in creatorIds }.values.toList()

    override suspend fun isRecipeAccessibleBy(userId: UUID, recipeId: UUID): Boolean {
        if (recipeId in inaccessibleRecipes) return false
        val record = recipes[recipeId] ?: return false
        return record.recipe.creatorId == userId.toString() || record.recipe.privacy == "PUBLIC"
    }

    override suspend fun accessibleRecipeIds(userId: UUID, recipeIds: Set<UUID>): Set<UUID> {
        val ownedOrPublic = recipeIds.filterTo(mutableSetOf()) { id ->
            id !in inaccessibleRecipes &&
                recipes[id]?.let { it.recipe.creatorId == userId.toString() || it.recipe.privacy == "PUBLIC" } == true
        }
        val householdId = activeHousehold[userId] ?: return ownedOrPublic
        return ownedOrPublic + (householdVisibleRecipeIds(householdId) intersect recipeIds)
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

    // ── Meal Plans ────────────────────────────────────────────────────────────

    override suspend fun isActiveHouseholdMember(userId: UUID, householdId: UUID): Boolean =
        activeHousehold[userId] == householdId

    override suspend fun getMealPlanForMember(uuid: UUID, userId: UUID): SyncMealPlanRecord? =
        mealPlans[uuid]?.takeIf { hasMemberAccess(it, userId) }

    override suspend fun mealPlanExists(uuid: UUID): Boolean = mealPlans.containsKey(uuid)

    override suspend fun upsertMealPlan(plan: SyncMealPlanDto, serverUpdatedAt: Instant): Boolean {
        val uuid = UUID.fromString(plan.uuid)
        val existing = mealPlans[uuid]
        // Mirrors PostgresSyncRepository: re-validates the conflict check against the live record
        // rather than trusting the caller's earlier read.
        if (existing != null && existing.serverUpdatedAtMillis > plan.updatedAt) {
            return false
        }
        // Mirrors PostgresSyncRepository: owner/household are set at insert only, an update never
        // touches them regardless of what this call's plan.ownerId/householdId say.
        val persisted = if (existing != null) {
            plan.copy(ownerId = existing.plan.ownerId, householdId = existing.plan.householdId)
        } else {
            plan
        }
        mealPlans[uuid] = SyncMealPlanRecord(plan = persisted, serverUpdatedAtMillis = serverUpdatedAt.toEpochMilliseconds())
        return true
    }

    override suspend fun findDeltaMealPlans(userId: UUID, sinceMillis: Long): List<SyncMealPlanRecord> {
        val normal = mealPlans.values.filter {
            it.serverUpdatedAtMillis > sinceMillis && hasMemberAccess(it, userId)
        }

        val tombstoneTimestamps = tombstoneTimestampsByPlanId(userId, sinceMillis)
        val tombstones = mealPlans.values.mapNotNull { record ->
            val timestamp = tombstoneTimestamps[UUID.fromString(record.plan.uuid)] ?: return@mapNotNull null
            record.copy(plan = record.plan.copy(deletedAt = timestamp))
        }

        return (normal + tombstones).distinctBy { it.plan.uuid }.sortedBy { it.serverUpdatedAtMillis }
    }

    /** Mirrors PostgresSyncRepository.tombstoneTimestampsByPlanId's two tombstone sources. */
    private fun tombstoneTimestampsByPlanId(userId: UUID, sinceMillis: Long): Map<UUID, Long> {
        val removal = removedHouseholdMembership[userId]
        val fromRemoval = if (removal != null && removal.second > sinceMillis) {
            val (removedHouseholdId, serverRemovedAtMillis) = removal
            mealPlans.values
                .filter {
                    val planId = UUID.fromString(it.plan.uuid)
                    (it.plan.householdId == removedHouseholdId.toString() ||
                        planDetachedFromHousehold[planId]?.first == removedHouseholdId) &&
                        it.plan.ownerId != userId.toString()
                }
                .associate { UUID.fromString(it.plan.uuid) to serverRemovedAtMillis }
        } else {
            emptyMap()
        }

        val myHouseholdId = activeHousehold[userId]
        val fromDetachment = planDetachedFromHousehold
            .filter { (planId, detachment) ->
                detachment.first == myHouseholdId && detachment.second > sinceMillis &&
                    mealPlans[planId]?.plan?.ownerId != userId.toString()
            }
            .mapValues { (_, detachment) -> detachment.second }

        return fromRemoval + fromDetachment
    }

    override suspend fun findHouseholdVisibleRecipeIds(userId: UUID): Set<UUID> {
        val householdId = activeHousehold[userId] ?: return emptySet()
        return householdVisibleRecipeIds(householdId)
    }

    override suspend fun isRecipeHouseholdVisible(userId: UUID, recipeId: UUID): Boolean {
        val householdId = activeHousehold[userId] ?: return false
        return recipeId in householdVisibleRecipeIds(householdId)
    }

    private fun hasMemberAccess(record: SyncMealPlanRecord, userId: UUID): Boolean =
        record.plan.ownerId == userId.toString() ||
            (record.plan.householdId != null && record.plan.householdId == activeHousehold[userId]?.toString())

    private fun householdVisibleRecipeIds(householdId: UUID): Set<UUID> =
        mealPlans.values
            .filter { it.plan.householdId == householdId.toString() && it.plan.deletedAt == null }
            .flatMap { record -> record.plan.days.flatMap { listOfNotNull(it.dinnerRecipeId, it.lunchRecipeId) } }
            .map { UUID.fromString(it) }
            .toSet()

    // ── Grocery List ──────────────────────────────────────────────────────────

    override suspend fun getGroceryListItem(mealPlanId: UUID, itemKey: String): SyncGroceryItemRecord? =
        groceryItems[mealPlanId to itemKey]

    override suspend fun upsertGroceryListItem(item: SyncGroceryListItem, serverUpdatedAt: Instant) {
        val key = UUID.fromString(item.mealPlanId) to item.itemKey
        groceryItems[key] = SyncGroceryItemRecord(item = item, serverUpdatedAtMillis = serverUpdatedAt.toEpochMilliseconds())
    }

    override suspend fun findDeltaGroceryListItems(userId: UUID, sinceMillis: Long): List<SyncGroceryItemRecord> {
        val normal = groceryItems.values.filter { record ->
            record.serverUpdatedAtMillis > sinceMillis &&
                mealPlans[UUID.fromString(record.item.mealPlanId)]?.let { hasMemberAccess(it, userId) } == true
        }

        val tombstoneTimestamps = tombstoneTimestampsByPlanId(userId, sinceMillis)
        val tombstones = groceryItems.values.mapNotNull { record ->
            val timestamp = tombstoneTimestamps[UUID.fromString(record.item.mealPlanId)] ?: return@mapNotNull null
            record.copy(item = record.item.copy(deletedAt = timestamp))
        }

        return (normal + tombstones)
            .distinctBy { it.item.mealPlanId to it.item.itemKey }
            .sortedBy { it.serverUpdatedAtMillis }
    }

    override suspend fun updateMealPlanStatus(planId: UUID, status: String, serverUpdatedAt: Instant) {
        val existing = mealPlans[planId] ?: return
        mealPlans[planId] = existing.copy(
            plan = existing.plan.copy(status = status),
            serverUpdatedAtMillis = serverUpdatedAt.toEpochMilliseconds()
        )
    }

    override suspend fun replaceMealPlanDays(planId: UUID, days: List<SyncMealPlanDayDto>) {
        val existing = mealPlans[planId] ?: return
        mealPlans[planId] = existing.copy(plan = existing.plan.copy(days = days))
    }

    override suspend fun findCandidateRecipeIds(
        userId: UUID?,
        recipeSource: String,
        dietaryRestrictionTags: List<String>,
        maxPrepTimeMinutes: Int?
    ): List<UUID> = candidateRecipeIds.toList()

    override suspend fun findRecipeRankingMetadata(recipeIds: Set<UUID>): Map<UUID, RecipeRankingMetadata> =
        recipeIds.mapNotNull { id -> rankingMetadataByRecipe[id]?.let { id to it } }.toMap()

    override suspend fun findRecentlyUsedRecipeIds(userId: UUID, excludePlanId: UUID, limit: Int): Set<UUID> =
        recentlyUsedElsewhereIds.toSet()
}
