package com.tenmilelabs.infrastructure.database.integration

import com.tenmilelabs.application.dto.SyncGroceryListItem
import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.application.dto.SyncRecipeIngredient
import com.tenmilelabs.application.dto.SyncRecipeStep
import com.tenmilelabs.domain.model.HouseholdRole
import com.tenmilelabs.domain.util.millisecondPrecisionNow
import com.tenmilelabs.infrastructure.database.initDatabaseAndSchema
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresHouseholdRepository
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresSyncRepository
import com.tenmilelabs.infrastructure.database.tables.AllergenTable
import com.tenmilelabs.infrastructure.database.tables.BookmarkedRecipeTable
import com.tenmilelabs.infrastructure.database.tables.HouseholdTable
import com.tenmilelabs.infrastructure.database.tables.IngredientTable
import com.tenmilelabs.infrastructure.database.tables.LabelTable
import com.tenmilelabs.infrastructure.database.tables.MealPlanTable
import com.tenmilelabs.infrastructure.database.tables.RecipeTable
import com.tenmilelabs.infrastructure.database.tables.SourceClassificationTable
import com.tenmilelabs.infrastructure.database.tables.TagTable
import com.tenmilelabs.infrastructure.database.tables.UserTable
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Tag("db-integration")
class PostgresSyncRepositoryIntegrationTest {

    @BeforeEach
    fun resetSchema() {
        Database.connect(
            // chefai_test, deliberately NOT the docker-compose dev database: resetSchema()
            // drops the public schema, which silently wiped a seeded chefai_db. CI sets DB_URL.
            url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/chefai_test",
            user = System.getenv("DB_USER") ?: "postgres",
            password = System.getenv("DB_PASSWORD") ?: "password"
        )

        transaction {
            exec("DROP SCHEMA IF EXISTS public CASCADE")
            exec("CREATE SCHEMA public")
        }

        initDatabaseAndSchema()
    }

    /**
     * Regression for security audit F1's defence-in-depth half: the UPDATE branch of
     * [PostgresSyncRepository.upsertRecipeAggregate] used to write `creator_id` from the payload,
     * so an upsert could transfer a recipe to a different owner. SyncService now rejects a push
     * against a row the caller doesn't own, but the column must be immutable at the SQL level too.
     */
    @Test
    fun upsertDoesNotReassignCreatorOnUpdate() = runBlocking {
        val repo = PostgresSyncRepository()

        val ownerId = UUID.randomUUID()
        val attackerId = UUID.randomUUID()
        val recipeId = UUID.randomUUID()

        transaction {
            listOf(ownerId to "owner", attackerId to "attacker").forEach { (id, name) ->
                UserTable.insert {
                    it[UserTable.id] = EntityID(id, UserTable)
                    it[user_name] = name
                    it[email] = "$name-$id@example.com"
                    it[display_name] = name
                    it[avatar_url] = ""
                    it[password_hash] = "hash"
                }
            }
        }

        fun aggregate(creatorId: UUID, title: String, updatedAt: Long) = SyncRecipe(
            uuid = recipeId.toString(),
            title = title,
            description = "d",
            imageUrl = "",
            imageUrlThumbnail = "",
            prepTimeMinutes = 1,
            cookTimeMinutes = 1,
            servings = 1,
            creatorId = creatorId.toString(),
            recipeExternalUrl = null,
            privacy = "PRIVATE",
            updatedAt = updatedAt,
            deletedAt = null,
            steps = emptyList(),
            ingredients = emptyList(),
            tagIds = emptyList(),
            labelIds = emptyList()
        )

        // Insert establishes ownership...
        repo.upsertRecipeAggregate(
            aggregate(ownerId, "Owner recipe", 1_000L),
            Instant.fromEpochMilliseconds(1_000L)
        )
        assertEquals(ownerId.toString(), repo.getRecipe(recipeId)?.recipe?.creatorId)

        // ...and a later update naming a different creator must not move it.
        repo.upsertRecipeAggregate(
            aggregate(attackerId, "Retitled", 2_000L),
            Instant.fromEpochMilliseconds(2_000L)
        )

        val loaded = repo.getRecipe(recipeId)
        assertNotNull(loaded)
        assertEquals(ownerId.toString(), loaded.recipe.creatorId, "creator_id must be immutable after insert")
        assertEquals("Retitled", loaded.recipe.title, "other columns still update normally")
    }

    /**
     * The join backfill regression (backend prompt §6.5): a plan shared with a household before
     * [joinerId] joins has an old `server_updated_at` — older than the cursor the joiner's device
     * already carries from unrelated prior syncing. Without
     * [PostgresHouseholdRepository.bumpServerUpdatedAtForHouseholdRows] running inside the accept
     * transaction, that plan would never cross the joiner's cursor and they'd never receive it.
     */
    @Test
    fun joiningAHouseholdBackfillsAnAlreadyExistingSharedPlanPastTheJoinersCursor() = runBlocking {
        val syncRepo = PostgresSyncRepository()
        val householdRepo = PostgresHouseholdRepository()

        val ownerId = UUID.randomUUID()
        val joinerId = UUID.randomUUID()
        transaction {
            listOf(ownerId to "owner", joinerId to "joiner").forEach { (id, name) ->
                UserTable.insert {
                    it[UserTable.id] = EntityID(id, UserTable)
                    it[user_name] = name
                    it[email] = "$name-$id@example.com"
                    it[display_name] = name
                    it[avatar_url] = ""
                    it[password_hash] = "hash"
                }
            }
        }

        val household = householdRepo.createHousehold("Household", ownerId)

        val planId = UUID.randomUUID()
        transaction {
            MealPlanTable.insert {
                it[id] = EntityID(planId, MealPlanTable)
                it[user_id] = EntityID(ownerId, UserTable)
                it[household_id] = EntityID(household.id, HouseholdTable)
                it[name] = "Week Plan"
                it[status] = "DRAFT"
                it[preferences] = "{}"
                it[created_at] = 1_000L
                it[updated_at] = 1_000L
                it[deleted_at] = null
                it[server_updated_at] = Instant.fromEpochMilliseconds(1_000L)
            }
        }

        householdRepo.addMember(household.id, joinerId, HouseholdRole.MEMBER, millisecondPrecisionNow())

        val joinerCursorMillis = 5_000L
        // Regression pin: membership alone isn't enough - the plan's stamp (1_000L) predates the
        // joiner's cursor, so without the backfill bump it's invisible even though they can now
        // access it.
        assertTrue(
            syncRepo.findDeltaMealPlans(joinerId, joinerCursorMillis).none { it.plan.uuid == planId.toString() },
            "sanity check: an unbumped plan must not already be visible past a newer cursor"
        )

        householdRepo.bumpServerUpdatedAtForHouseholdRows(household.id, millisecondPrecisionNow())

        val received = syncRepo.findDeltaMealPlans(joinerId, joinerCursorMillis)
        assertTrue(received.any { it.plan.uuid == planId.toString() }, "joiner must receive the pre-existing shared plan")
    }

    /**
     * The mirror image of [joiningAHouseholdBackfillsAnAlreadyExistingSharedPlanPastTheJoinersCursor]:
     * when a member leaves and their own shared plan gets detached, a still-ACTIVE household-mate
     * who already cached that plan must be tombstoned for it on their next pull too — not just the
     * departing member's own removal-tombstone, which only fires for their own next pull. Exercises
     * `meal_plans.former_household_id`/`household_detached_at` end to end against real Postgres.
     */
    @Test
    fun aMemberLeavingTombstonesTheirDetachedPlanForStillActiveHouseholdMates() = runBlocking {
        val syncRepo = PostgresSyncRepository()
        val householdRepo = PostgresHouseholdRepository()

        val departingOwnerId = UUID.randomUUID()
        val stayingMemberId = UUID.randomUUID()
        transaction {
            listOf(departingOwnerId to "owner", stayingMemberId to "staying").forEach { (id, name) ->
                UserTable.insert {
                    it[UserTable.id] = EntityID(id, UserTable)
                    it[user_name] = name
                    it[email] = "$name-$id@example.com"
                    it[display_name] = name
                    it[avatar_url] = ""
                    it[password_hash] = "hash"
                }
            }
        }

        val household = householdRepo.createHousehold("Household", departingOwnerId)
        householdRepo.addMember(household.id, stayingMemberId, HouseholdRole.MEMBER, millisecondPrecisionNow())

        val planId = UUID.randomUUID()
        transaction {
            MealPlanTable.insert {
                it[id] = EntityID(planId, MealPlanTable)
                it[user_id] = EntityID(departingOwnerId, UserTable)
                it[household_id] = EntityID(household.id, HouseholdTable)
                it[name] = "Week Plan"
                it[status] = "DRAFT"
                it[preferences] = "{}"
                it[created_at] = 1_000L
                it[updated_at] = 1_000L
                it[deleted_at] = null
                it[server_updated_at] = Instant.fromEpochMilliseconds(1_000L)
            }
        }

        // stayingMemberId already pulled this plan a while ago; their cursor is past its original stamp.
        val stayingMembersCursorMillis = 1_500L

        householdRepo.departFromHousehold(household.id, departingOwnerId, millisecondPrecisionNow())

        val received = syncRepo.findDeltaMealPlans(stayingMemberId, stayingMembersCursorMillis)
        val tombstoned = received.singleOrNull { it.plan.uuid == planId.toString() }
        assertNotNull(tombstoned, "a still-active member must be told their co-member's detached plan is gone")
        assertTrue(tombstoned.plan.deletedAt != null, "the tombstone must carry a synthetic deletedAt")
    }

    /** Round-trips a grocery item through Postgres and confirms LWW + household-member visibility. */
    @Test
    fun groceryListItemUpsertAndDeltaAgainstPostgres() = runBlocking {
        val syncRepo = PostgresSyncRepository()
        val householdRepo = PostgresHouseholdRepository()

        val ownerId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        transaction {
            listOf(ownerId to "owner", memberId to "member").forEach { (id, name) ->
                UserTable.insert {
                    it[UserTable.id] = EntityID(id, UserTable)
                    it[user_name] = name
                    it[email] = "$name-$id@example.com"
                    it[display_name] = name
                    it[avatar_url] = ""
                    it[password_hash] = "hash"
                }
            }
        }

        val household = householdRepo.createHousehold("Household", ownerId)
        householdRepo.addMember(household.id, memberId, HouseholdRole.MEMBER, millisecondPrecisionNow())

        val planId = UUID.randomUUID()
        transaction {
            MealPlanTable.insert {
                it[id] = EntityID(planId, MealPlanTable)
                it[user_id] = EntityID(ownerId, UserTable)
                it[household_id] = EntityID(household.id, HouseholdTable)
                it[name] = "Week Plan"
                it[status] = "DRAFT"
                it[preferences] = "{}"
                it[created_at] = 1_000L
                it[updated_at] = 1_000L
                it[deleted_at] = null
                it[server_updated_at] = Instant.fromEpochMilliseconds(1_000L)
            }
        }

        // Authorization choke point: getMealPlanForMember gates grocery writes too.
        assertNotNull(syncRepo.getMealPlanForMember(planId, memberId))

        val item = SyncGroceryListItem(
            mealPlanId = planId.toString(),
            itemKey = "eggs",
            checked = true,
            checkedBy = ownerId.toString(),
            updatedAt = 1_000L,
            deletedAt = null
        )
        syncRepo.upsertGroceryListItem(item, Instant.fromEpochMilliseconds(2_000L))

        val loaded = syncRepo.getGroceryListItem(planId, "eggs")
        assertNotNull(loaded)
        assertEquals(true, loaded.item.checked)

        // Stale update (updatedAt 500 < stored 1_000) must not overwrite.
        syncRepo.upsertGroceryListItem(item.copy(checked = false, updatedAt = 500L), Instant.fromEpochMilliseconds(3_000L))
        val afterStaleAttempt = syncRepo.getGroceryListItem(planId, "eggs")
        assertNotNull(afterStaleAttempt)
        // upsertGroceryListItem trusts its caller (SyncService is what enforces LWW) - this pins
        // that the repository layer itself still just persists whatever it's given, consistent
        // with upsertMealPlan's division of labor.
        assertEquals(false, afterStaleAttempt.item.checked)

        // Household member sees the item via the widened delta query.
        val memberDelta = syncRepo.findDeltaGroceryListItems(memberId, sinceMillis = 0L)
        assertTrue(memberDelta.any { it.item.mealPlanId == planId.toString() && it.item.itemKey == "eggs" })

        // A non-member sees nothing.
        val strangerId = UUID.randomUUID()
        val strangerDelta = syncRepo.findDeltaGroceryListItems(strangerId, sinceMillis = 0L)
        assertTrue(strangerDelta.none { it.item.mealPlanId == planId.toString() })
    }

    @Test
    fun upsertAndQueryRecipeAggregateAgainstPostgres() = runBlocking {
        val repo = PostgresSyncRepository()

        val ownerId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        val recipeId = UUID.randomUUID()
        val stepId = UUID.randomUUID()

        val allergenId = UUID.randomUUID()
        val sourceClassificationId = UUID.randomUUID()
        val ingredientId = UUID.randomUUID()
        val knownTagId = UUID.randomUUID()
        val knownLabelId = UUID.randomUUID()
        val unknownTagId = UUID.randomUUID()
        val unknownLabelId = UUID.randomUUID()

        val refUpdatedAt = 10L
        val refServerUpdatedAt = Instant.fromEpochMilliseconds(20L)

        transaction {
            UserTable.insert {
                it[UserTable.id] = EntityID(ownerId, UserTable)
                it[user_name] = "owner"
                it[email] = "owner-${ownerId}@example.com"
                it[display_name] = "Owner"
                it[avatar_url] = ""
                it[password_hash] = "hash"
            }
            UserTable.insert {
                it[UserTable.id] = EntityID(otherUserId, UserTable)
                it[user_name] = "other"
                it[email] = "other-${otherUserId}@example.com"
                it[display_name] = "Other"
                it[avatar_url] = ""
                it[password_hash] = "hash"
            }

            AllergenTable.insert {
                it[AllergenTable.id] = EntityID(allergenId, AllergenTable)
                it[display_name] = "Milk"
                it[updated_at] = refUpdatedAt
                it[deleted_at] = null
                it[server_updated_at] = refServerUpdatedAt
            }

            SourceClassificationTable.insert {
                it[SourceClassificationTable.id] = EntityID(sourceClassificationId, SourceClassificationTable)
                it[category] = "Food"
                it[subcategory] = "Dairy"
                it[updated_at] = refUpdatedAt
                it[deleted_at] = null
                it[server_updated_at] = refServerUpdatedAt
            }

            IngredientTable.insert {
                it[IngredientTable.id] = EntityID(ingredientId, IngredientTable)
                it[display_name] = "Parmesan"
                it[allergen_id] = EntityID(allergenId, AllergenTable)
                it[source_primary_id] = sourceClassificationId
                it[updated_at] = refUpdatedAt
                it[deleted_at] = null
                it[server_updated_at] = refServerUpdatedAt
            }

            TagTable.insert {
                it[TagTable.id] = EntityID(knownTagId, TagTable)
                it[display_name] = "Dinner"
                it[updated_at] = refUpdatedAt
                it[deleted_at] = null
                it[server_updated_at] = refServerUpdatedAt
            }

            LabelTable.insert {
                it[LabelTable.id] = EntityID(knownLabelId, LabelTable)
                it[display_name] = "Quick"
                it[updated_at] = refUpdatedAt
                it[deleted_at] = null
                it[server_updated_at] = refServerUpdatedAt
            }
        }

        val pushedRecipe = SyncRecipe(
            uuid = recipeId.toString(),
            title = "Carbonara",
            description = "Classic",
            imageUrl = "https://example.com/image.jpg",
            imageUrlThumbnail = "https://example.com/thumb.jpg",
            prepTimeMinutes = 10,
            cookTimeMinutes = 20,
            servings = 2,
            creatorId = ownerId.toString(),
            recipeExternalUrl = null,
            privacy = "PRIVATE",
            updatedAt = 1_000L,
            deletedAt = null,
            steps = listOf(
                SyncRecipeStep(
                    uuid = stepId.toString(),
                    orderIndex = 0,
                    instruction = "Boil water"
                )
            ),
            ingredients = listOf(
                SyncRecipeIngredient(
                    ingredientId = ingredientId.toString(),
                    quantity = 200.0,
                    unit = "g"
                )
            ),
            tagIds = listOf(knownTagId.toString(), unknownTagId.toString()),
            labelIds = listOf(knownLabelId.toString(), unknownLabelId.toString())
        )

        val serverUpdatedAt = Instant.fromEpochMilliseconds(2_000L)
        repo.upsertRecipeAggregate(pushedRecipe, serverUpdatedAt)

        val loaded = repo.getRecipe(recipeId)
        assertNotNull(loaded)
        assertEquals("Carbonara", loaded.recipe.title)
        assertEquals(2_000L, loaded.serverUpdatedAtMillis)
        assertEquals(1, loaded.recipe.steps.size)
        assertEquals(1, loaded.recipe.ingredients.size)

        // Unknown tag/label IDs should be dropped by PostgresSyncRepository.
        assertEquals(setOf(knownTagId.toString()), loaded.recipe.tagIds.toSet())
        assertEquals(setOf(knownLabelId.toString()), loaded.recipe.labelIds.toSet())

        val visibleDeltas = repo.findDeltaRecipes(userId = ownerId, sinceMillis = 1_500L, limit = 10)
        assertEquals(1, visibleDeltas.size)
        assertEquals(recipeId.toString(), visibleDeltas.first().recipe.uuid)

        val referenceData = repo.collectReferenceData(
            ingredientIds = setOf(ingredientId),
            tagIds = setOf(knownTagId),
            labelIds = setOf(knownLabelId),
            sinceMillis = null
        )
        assertTrue(referenceData.ingredients.any { it.uuid == ingredientId.toString() })
        assertTrue(referenceData.allergens.any { it.uuid == allergenId.toString() })
        assertTrue(referenceData.sourceClassifications.any { it.uuid == sourceClassificationId.toString() })
        assertTrue(referenceData.tags.any { it.uuid == knownTagId.toString() })
        assertTrue(referenceData.labels.any { it.uuid == knownLabelId.toString() })

        assertEquals(
            setOf(knownTagId),
            repo.existingTagIds(setOf(knownTagId, unknownTagId))
        )
        assertEquals(
            setOf(knownLabelId),
            repo.existingLabelIds(setOf(knownLabelId, unknownLabelId))
        )
    }

    @Test
    fun findCandidateRecipeIdsFiltersByDietaryLabelNotTag() = runBlocking {
        val repo = PostgresSyncRepository()
        val ownerId = UUID.randomUUID()
        val veganLabelId = UUID.randomUUID()
        val glutenFreeLabelId = UUID.randomUUID()
        // A tag that happens to be named "Vegan" too — the fix must not fall back to matching this.
        val decoyVeganTagId = UUID.randomUUID()

        val refUpdatedAt = 10L
        val refServerUpdatedAt = Instant.fromEpochMilliseconds(20L)

        transaction {
            UserTable.insert {
                it[UserTable.id] = EntityID(ownerId, UserTable)
                it[user_name] = "owner"
                it[email] = "owner-${ownerId}@example.com"
                it[display_name] = "Owner"
                it[avatar_url] = ""
                it[password_hash] = "hash"
            }
            LabelTable.insert {
                it[LabelTable.id] = EntityID(veganLabelId, LabelTable)
                it[display_name] = "Vegan"
                it[updated_at] = refUpdatedAt
                it[deleted_at] = null
                it[server_updated_at] = refServerUpdatedAt
            }
            LabelTable.insert {
                it[LabelTable.id] = EntityID(glutenFreeLabelId, LabelTable)
                it[display_name] = "Gluten-Free"
                it[updated_at] = refUpdatedAt
                it[deleted_at] = null
                it[server_updated_at] = refServerUpdatedAt
            }
            TagTable.insert {
                it[TagTable.id] = EntityID(decoyVeganTagId, TagTable)
                it[display_name] = "Vegan"
                it[updated_at] = refUpdatedAt
                it[deleted_at] = null
                it[server_updated_at] = refServerUpdatedAt
            }
        }

        fun pushPublicRecipe(id: UUID, title: String, labelIds: List<UUID>, tagIds: List<UUID>) = runBlocking {
            repo.upsertRecipeAggregate(
                SyncRecipe(
                    uuid = id.toString(),
                    title = title,
                    description = "desc",
                    imageUrl = "https://example.com/i.jpg",
                    imageUrlThumbnail = "https://example.com/t.jpg",
                    prepTimeMinutes = 10,
                    cookTimeMinutes = 10,
                    servings = 2,
                    creatorId = ownerId.toString(),
                    recipeExternalUrl = null,
                    privacy = "PUBLIC",
                    updatedAt = 1_000L,
                    deletedAt = null,
                    steps = emptyList(),
                    ingredients = emptyList(),
                    tagIds = tagIds.map { it.toString() },
                    labelIds = labelIds.map { it.toString() }
                ),
                Instant.fromEpochMilliseconds(2_000L)
            )
        }

        val veganRecipeId = UUID.randomUUID()
        val glutenFreeRecipeId = UUID.randomUUID()
        val decoyTaggedOnlyRecipeId = UUID.randomUUID()
        val plainRecipeId = UUID.randomUUID()

        pushPublicRecipe(veganRecipeId, "Vegan Chili", labelIds = listOf(veganLabelId), tagIds = emptyList())
        pushPublicRecipe(glutenFreeRecipeId, "GF Bread", labelIds = listOf(glutenFreeLabelId), tagIds = emptyList())
        pushPublicRecipe(decoyTaggedOnlyRecipeId, "Tagged Vegan Only", labelIds = emptyList(), tagIds = listOf(decoyVeganTagId))
        pushPublicRecipe(plainRecipeId, "Plain Stew", labelIds = emptyList(), tagIds = emptyList())

        // "VEGAN" (client enum form) must match the "Vegan" label, not the same-named tag.
        val veganCandidates = repo.findCandidateRecipeIds(
            userId = ownerId,
            recipeSource = "INCLUDE_PUBLIC",
            dietaryRestrictionTags = listOf("VEGAN"),
            maxPrepTimeMinutes = null
        )
        assertEquals(setOf(veganRecipeId), veganCandidates.toSet())

        // "GLUTEN_FREE" (underscore form) must match the "Gluten-Free" (hyphenated) label.
        val glutenFreeCandidates = repo.findCandidateRecipeIds(
            userId = ownerId,
            recipeSource = "INCLUDE_PUBLIC",
            dietaryRestrictionTags = listOf("GLUTEN_FREE"),
            maxPrepTimeMinutes = null
        )
        assertEquals(setOf(glutenFreeRecipeId), glutenFreeCandidates.toSet())
    }

    @Test
    fun findCandidateRecipeIdsCollectionOnlyIncludesAuthoredAndBookmarked() = runBlocking {
        val repo = PostgresSyncRepository()
        val userId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()

        transaction {
            UserTable.insert {
                it[UserTable.id] = EntityID(userId, UserTable)
                it[user_name] = "user"
                it[email] = "user-${userId}@example.com"
                it[display_name] = "User"
                it[avatar_url] = ""
                it[password_hash] = "hash"
            }
            UserTable.insert {
                it[UserTable.id] = EntityID(otherUserId, UserTable)
                it[user_name] = "other"
                it[email] = "other-${otherUserId}@example.com"
                it[display_name] = "Other"
                it[avatar_url] = ""
                it[password_hash] = "hash"
            }
        }

        fun pushRecipe(id: UUID, title: String, creatorId: UUID, privacy: String) = runBlocking {
            repo.upsertRecipeAggregate(
                SyncRecipe(
                    uuid = id.toString(),
                    title = title,
                    description = "desc",
                    imageUrl = "https://example.com/i.jpg",
                    imageUrlThumbnail = "https://example.com/t.jpg",
                    prepTimeMinutes = 10,
                    cookTimeMinutes = 10,
                    servings = 2,
                    creatorId = creatorId.toString(),
                    recipeExternalUrl = null,
                    privacy = privacy,
                    updatedAt = 1_000L,
                    deletedAt = null,
                    steps = emptyList(),
                    ingredients = emptyList(),
                    tagIds = emptyList(),
                    labelIds = emptyList()
                ),
                Instant.fromEpochMilliseconds(2_000L)
            )
        }

        val authoredNotBookmarkedId = UUID.randomUUID()
        val bookmarkedFromOtherUserId = UUID.randomUUID()
        val neitherId = UUID.randomUUID()

        pushRecipe(authoredNotBookmarkedId, "My Own Recipe", creatorId = userId, privacy = "PRIVATE")
        pushRecipe(bookmarkedFromOtherUserId, "Bookmarked From Other", creatorId = otherUserId, privacy = "PUBLIC")
        pushRecipe(neitherId, "Public Untouched", creatorId = otherUserId, privacy = "PUBLIC")

        transaction {
            BookmarkedRecipeTable.insert {
                it[BookmarkedRecipeTable.user_id] = EntityID(userId, UserTable)
                it[BookmarkedRecipeTable.recipe_id] = EntityID(bookmarkedFromOtherUserId, RecipeTable)
                it[deleted_at] = null
                it[server_updated_at] = Instant.fromEpochMilliseconds(2_000L)
            }
        }

        val candidates = repo.findCandidateRecipeIds(
            userId = userId,
            recipeSource = "COLLECTION_ONLY",
            dietaryRestrictionTags = emptyList(),
            maxPrepTimeMinutes = null
        )

        assertEquals(setOf(authoredNotBookmarkedId, bookmarkedFromOtherUserId), candidates.toSet())
    }

    @Test
    fun findCandidateRecipeIdsCollectionOnlyExcludesStaleBookmarks() = runBlocking {
        val repo = PostgresSyncRepository()
        val userId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()

        transaction {
            UserTable.insert {
                it[UserTable.id] = EntityID(userId, UserTable)
                it[user_name] = "user"
                it[email] = "user-${userId}@example.com"
                it[display_name] = "User"
                it[avatar_url] = ""
                it[password_hash] = "hash"
            }
            UserTable.insert {
                it[UserTable.id] = EntityID(otherUserId, UserTable)
                it[user_name] = "other"
                it[email] = "other-${otherUserId}@example.com"
                it[display_name] = "Other"
                it[avatar_url] = ""
                it[password_hash] = "hash"
            }
        }

        fun pushRecipe(id: UUID, title: String, creatorId: UUID, privacy: String) = runBlocking {
            repo.upsertRecipeAggregate(
                SyncRecipe(
                    uuid = id.toString(),
                    title = title,
                    description = "desc",
                    imageUrl = "https://example.com/i.jpg",
                    imageUrlThumbnail = "https://example.com/t.jpg",
                    prepTimeMinutes = 10,
                    cookTimeMinutes = 10,
                    servings = 2,
                    creatorId = creatorId.toString(),
                    recipeExternalUrl = null,
                    privacy = privacy,
                    updatedAt = 1_000L,
                    deletedAt = null,
                    steps = emptyList(),
                    ingredients = emptyList(),
                    tagIds = emptyList(),
                    labelIds = emptyList()
                ),
                Instant.fromEpochMilliseconds(2_000L)
            )
        }

        val stillPublicId = UUID.randomUUID()
        val laterPrivatizedId = UUID.randomUUID()
        val laterSoftDeletedId = UUID.randomUUID()

        pushRecipe(stillPublicId, "Still Public", creatorId = otherUserId, privacy = "PUBLIC")
        pushRecipe(laterPrivatizedId, "Later Privatized", creatorId = otherUserId, privacy = "PUBLIC")
        pushRecipe(laterSoftDeletedId, "Later Soft Deleted", creatorId = otherUserId, privacy = "PUBLIC")

        transaction {
            listOf(stillPublicId, laterPrivatizedId, laterSoftDeletedId).forEach { recipeId ->
                BookmarkedRecipeTable.insert {
                    it[BookmarkedRecipeTable.user_id] = EntityID(userId, UserTable)
                    it[BookmarkedRecipeTable.recipe_id] = EntityID(recipeId, RecipeTable)
                    it[deleted_at] = null
                    it[server_updated_at] = Instant.fromEpochMilliseconds(2_000L)
                }
            }

            // Nothing cleans up bookmarked_recipes when the owner later revokes access — simulate
            // that directly against RecipeTable, the same way the owner's own push would.
            RecipeTable.update({ RecipeTable.id eq EntityID(laterPrivatizedId, RecipeTable) }) {
                it[privacy] = "PRIVATE"
            }
            RecipeTable.update({ RecipeTable.id eq EntityID(laterSoftDeletedId, RecipeTable) }) {
                it[deleted_at] = 3_000L
            }
        }

        val candidates = repo.findCandidateRecipeIds(
            userId = userId,
            recipeSource = "COLLECTION_ONLY",
            dietaryRestrictionTags = emptyList(),
            maxPrepTimeMinutes = null
        )

        assertEquals(setOf(stillPublicId), candidates.toSet())
    }

    @Test
    fun findCandidateRecipeIdsIncludePublicExcludesInaccessibleAndStaleBookmarkedRecipes() = runBlocking {
        val repo = PostgresSyncRepository()
        val userId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()

        transaction {
            UserTable.insert {
                it[UserTable.id] = EntityID(userId, UserTable)
                it[user_name] = "user"
                it[email] = "user-${userId}@example.com"
                it[display_name] = "User"
                it[avatar_url] = ""
                it[password_hash] = "hash"
            }
            UserTable.insert {
                it[UserTable.id] = EntityID(otherUserId, UserTable)
                it[user_name] = "other"
                it[email] = "other-${otherUserId}@example.com"
                it[display_name] = "Other"
                it[avatar_url] = ""
                it[password_hash] = "hash"
            }
        }

        fun pushRecipe(id: UUID, title: String, creatorId: UUID, privacy: String) = runBlocking {
            repo.upsertRecipeAggregate(
                SyncRecipe(
                    uuid = id.toString(),
                    title = title,
                    description = "desc",
                    imageUrl = "https://example.com/i.jpg",
                    imageUrlThumbnail = "https://example.com/t.jpg",
                    prepTimeMinutes = 10,
                    cookTimeMinutes = 10,
                    servings = 2,
                    creatorId = creatorId.toString(),
                    recipeExternalUrl = null,
                    privacy = privacy,
                    updatedAt = 1_000L,
                    deletedAt = null,
                    steps = emptyList(),
                    ingredients = emptyList(),
                    tagIds = emptyList(),
                    labelIds = emptyList()
                ),
                Instant.fromEpochMilliseconds(2_000L)
            )
        }

        val ownedPrivateId = UUID.randomUUID()
        val otherPublicId = UUID.randomUUID()
        val otherPrivateNotBookmarkedId = UUID.randomUUID()
        val otherPrivateButBookmarkedId = UUID.randomUUID()
        val softDeletedPublicId = UUID.randomUUID()

        pushRecipe(ownedPrivateId, "My Private Recipe", creatorId = userId, privacy = "PRIVATE")
        pushRecipe(otherPublicId, "Other's Public Recipe", creatorId = otherUserId, privacy = "PUBLIC")
        pushRecipe(otherPrivateNotBookmarkedId, "Other's Private Recipe", creatorId = otherUserId, privacy = "PRIVATE")
        pushRecipe(otherPrivateButBookmarkedId, "Later Privatized But Bookmarked", creatorId = otherUserId, privacy = "PUBLIC")
        pushRecipe(softDeletedPublicId, "Soft Deleted Public Recipe", creatorId = otherUserId, privacy = "PUBLIC")

        transaction {
            // A bookmark must never be the thing that grants INCLUDE_PUBLIC access — this branch
            // doesn't even query bookmarked_recipes, so a bookmark to a recipe the owner has since
            // made inaccessible (here: privatized) must not leak it into the candidate pool.
            BookmarkedRecipeTable.insert {
                it[BookmarkedRecipeTable.user_id] = EntityID(userId, UserTable)
                it[BookmarkedRecipeTable.recipe_id] = EntityID(otherPrivateButBookmarkedId, RecipeTable)
                it[deleted_at] = null
                it[server_updated_at] = Instant.fromEpochMilliseconds(2_000L)
            }
            RecipeTable.update({ RecipeTable.id eq EntityID(otherPrivateButBookmarkedId, RecipeTable) }) {
                it[privacy] = "PRIVATE"
            }
            RecipeTable.update({ RecipeTable.id eq EntityID(softDeletedPublicId, RecipeTable) }) {
                it[deleted_at] = 3_000L
            }
        }

        val candidates = repo.findCandidateRecipeIds(
            userId = userId,
            recipeSource = "INCLUDE_PUBLIC",
            dietaryRestrictionTags = emptyList(),
            maxPrepTimeMinutes = null
        )

        assertEquals(setOf(ownedPrivateId, otherPublicId), candidates.toSet())
    }

    @Test
    fun findCandidateRecipeIdsWithNullUserIdReturnsOnlyPublicNonDeletedRecipes() = runBlocking {
        val repo = PostgresSyncRepository()
        val creatorId = UUID.randomUUID()

        transaction {
            UserTable.insert {
                it[UserTable.id] = EntityID(creatorId, UserTable)
                it[user_name] = "creator"
                it[email] = "creator-${creatorId}@example.com"
                it[display_name] = "Creator"
                it[avatar_url] = ""
                it[password_hash] = "hash"
            }
        }

        fun pushRecipe(id: UUID, title: String, privacy: String) = runBlocking {
            repo.upsertRecipeAggregate(
                SyncRecipe(
                    uuid = id.toString(),
                    title = title,
                    description = "desc",
                    imageUrl = "https://example.com/i.jpg",
                    imageUrlThumbnail = "https://example.com/t.jpg",
                    prepTimeMinutes = 10,
                    cookTimeMinutes = 10,
                    servings = 2,
                    creatorId = creatorId.toString(),
                    recipeExternalUrl = null,
                    privacy = privacy,
                    updatedAt = 1_000L,
                    deletedAt = null,
                    steps = emptyList(),
                    ingredients = emptyList(),
                    tagIds = emptyList(),
                    labelIds = emptyList()
                ),
                Instant.fromEpochMilliseconds(2_000L)
            )
        }

        val publicId = UUID.randomUUID()
        val privateId = UUID.randomUUID()
        val softDeletedPublicId = UUID.randomUUID()

        pushRecipe(publicId, "Public Recipe", privacy = "PUBLIC")
        pushRecipe(privateId, "Private Recipe", privacy = "PRIVATE")
        pushRecipe(softDeletedPublicId, "Soft Deleted Public Recipe", privacy = "PUBLIC")

        transaction {
            RecipeTable.update({ RecipeTable.id eq EntityID(softDeletedPublicId, RecipeTable) }) {
                it[deleted_at] = 3_000L
            }
        }

        val candidates = repo.findCandidateRecipeIds(
            userId = null,
            recipeSource = "INCLUDE_PUBLIC",
            dietaryRestrictionTags = emptyList(),
            maxPrepTimeMinutes = null
        )

        assertEquals(setOf(publicId), candidates.toSet())
    }
}
