package com.tenmilelabs.infrastructure.database.integration

import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.application.dto.SyncRecipeIngredient
import com.tenmilelabs.infrastructure.database.initDatabaseAndSchema
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresSyncRepository
import com.tenmilelabs.infrastructure.database.tables.IngredientTable
import com.tenmilelabs.infrastructure.database.tables.MealPlanDayTable
import com.tenmilelabs.infrastructure.database.tables.MealPlanTable
import com.tenmilelabs.infrastructure.database.tables.RecipeTable
import com.tenmilelabs.infrastructure.database.tables.SourceClassificationTable
import com.tenmilelabs.infrastructure.database.tables.UserTable
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("db-integration")
class PostgresRecipeRankingIntegrationTest {

    @BeforeEach
    fun resetSchema() {
        Database.connect(
            url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/chefai_db",
            user = System.getenv("DB_USER") ?: "postgres",
            password = System.getenv("DB_PASSWORD") ?: "password"
        )

        transaction {
            exec("DROP SCHEMA IF EXISTS public CASCADE")
            exec("CREATE SCHEMA public")
        }

        initDatabaseAndSchema()
    }

    // ── findRecipeRankingMetadata ────────────────────────────────────────────

    @Test
    fun findRecipeRankingMetadataReturnsServingsAndModeCategory() = runBlocking {
        val repo = PostgresSyncRepository()
        val ownerId = seedUser()
        val meatCategoryId = seedSourceClassification("Meat")
        val vegCategoryId = seedSourceClassification("Vegetable")
        val chickenId = seedIngredient("Chicken", meatCategoryId)
        val beefId = seedIngredient("Beef", meatCategoryId)
        val carrotId = seedIngredient("Carrot", vegCategoryId)
        val unclassifiedId = seedIngredient("Mystery Powder", sourceClassificationId = null)

        // Two Meat-classified ingredients + one Vegetable-classified: mode should be Meat.
        val mixedRecipeId = pushRecipe(
            repo, ownerId, servings = 6,
            ingredientIds = listOf(chickenId, beefId, carrotId)
        )
        // Only an unclassified ingredient: dominant category should be null.
        val unclassifiedRecipeId = pushRecipe(
            repo, ownerId, servings = 2,
            ingredientIds = listOf(unclassifiedId)
        )

        val metadata = repo.findRecipeRankingMetadata(setOf(mixedRecipeId, unclassifiedRecipeId))

        assertEquals(6, metadata[mixedRecipeId]?.servings)
        assertEquals("Meat", metadata[mixedRecipeId]?.dominantCategory)
        assertEquals(2, metadata[unclassifiedRecipeId]?.servings)
        assertNull(metadata[unclassifiedRecipeId]?.dominantCategory)
    }

    // ── findRecentlyUsedRecipeIds ────────────────────────────────────────────

    @Test
    fun findRecentlyUsedRecipeIdsOnlyCountsReadyPlansAndRespectsLimit() = runBlocking {
        val repo = PostgresSyncRepository()
        val userId = seedUser()
        val recipeIds = (1..5).map { seedBareRecipe(userId) }
        val currentlyGeneratingPlanId = UUID.randomUUID()

        // 4 READY plans (newest to oldest), each pointing at a distinct recipe as dinner.
        recipeIds.take(4).forEachIndexed { index, recipeId ->
            seedMealPlan(
                userId = userId,
                status = "READY",
                createdAt = 4000L - (index * 1000L),
                dinnerRecipeId = recipeId
            )
        }
        // A DRAFT plan (never generated) — must not count toward recent history.
        seedMealPlan(userId = userId, status = "DRAFT", createdAt = 5000L, dinnerRecipeId = recipeIds[4])

        val recentlyUsed = repo.findRecentlyUsedRecipeIds(userId, excludePlanId = currentlyGeneratingPlanId, limit = 3)

        // Only the 3 most recent READY plans' recipes should be included.
        assertEquals(3, recentlyUsed.size)
        assertTrue(recipeIds[4] !in recentlyUsed) // DRAFT plan's recipe excluded
        assertTrue(recipeIds[3] !in recentlyUsed) // 4th-most-recent READY plan, beyond the limit
    }

    @Test
    fun findRecentlyUsedRecipeIdsExcludesTheGivenPlanAndSoftDeletedPlans() = runBlocking {
        val repo = PostgresSyncRepository()
        val userId = seedUser()
        val excludedRecipeId = seedBareRecipe(userId)
        val deletedPlanRecipeId = seedBareRecipe(userId)
        val includedRecipeId = seedBareRecipe(userId)

        val excludedPlanId = seedMealPlan(userId = userId, status = "READY", createdAt = 1000L, dinnerRecipeId = excludedRecipeId)
        seedMealPlan(userId = userId, status = "READY", createdAt = 2000L, dinnerRecipeId = deletedPlanRecipeId, deletedAt = 2000L)
        seedMealPlan(userId = userId, status = "READY", createdAt = 3000L, dinnerRecipeId = includedRecipeId)

        val recentlyUsed = repo.findRecentlyUsedRecipeIds(userId, excludePlanId = excludedPlanId, limit = 10)

        assertEquals(setOf(includedRecipeId), recentlyUsed)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun seedUser(): UUID {
        val userId = UUID.randomUUID()
        transaction {
            UserTable.insert {
                it[UserTable.id] = EntityID(userId, UserTable)
                it[user_name] = "user-${userId.toString().take(8)}"
                it[email] = "user-${userId}@example.com"
                it[display_name] = "User"
                it[avatar_url] = ""
                it[password_hash] = "hash"
            }
        }
        return userId
    }

    private fun seedSourceClassification(category: String): UUID {
        val id = UUID.randomUUID()
        transaction {
            SourceClassificationTable.insert {
                it[SourceClassificationTable.id] = EntityID(id, SourceClassificationTable)
                it[SourceClassificationTable.category] = category
                it[subcategory] = null
                it[updated_at] = 0L
                it[deleted_at] = null
                it[server_updated_at] = Instant.fromEpochMilliseconds(0L)
            }
        }
        return id
    }

    private fun seedIngredient(name: String, sourceClassificationId: UUID?): UUID {
        val id = UUID.randomUUID()
        transaction {
            IngredientTable.insert {
                it[IngredientTable.id] = EntityID(id, IngredientTable)
                it[display_name] = name
                it[allergen_id] = null
                it[source_primary_id] = sourceClassificationId
                it[updated_at] = 0L
                it[deleted_at] = null
                it[server_updated_at] = Instant.fromEpochMilliseconds(0L)
            }
        }
        return id
    }

    private fun pushRecipe(repo: PostgresSyncRepository, ownerId: UUID, servings: Int, ingredientIds: List<UUID>): UUID = runBlocking {
        val recipeId = UUID.randomUUID()
        repo.upsertRecipeAggregate(
            SyncRecipe(
                uuid = recipeId.toString(),
                title = "Recipe",
                description = "desc",
                imageUrl = "https://example.com/i.jpg",
                imageUrlThumbnail = "https://example.com/t.jpg",
                prepTimeMinutes = 10,
                cookTimeMinutes = 10,
                servings = servings,
                creatorId = ownerId.toString(),
                recipeExternalUrl = null,
                privacy = "PUBLIC",
                updatedAt = 1_000L,
                deletedAt = null,
                steps = emptyList(),
                ingredients = ingredientIds.map { SyncRecipeIngredient(ingredientId = it.toString(), quantity = 1.0, unit = "unit") },
                tagIds = emptyList(),
                labelIds = emptyList()
            ),
            Instant.fromEpochMilliseconds(2_000L)
        )
        recipeId
    }

    private fun seedBareRecipe(ownerId: UUID): UUID = pushRecipe(PostgresSyncRepository(), ownerId, servings = 2, ingredientIds = emptyList())

    private fun seedMealPlan(
        userId: UUID,
        status: String,
        createdAt: Long,
        dinnerRecipeId: UUID,
        deletedAt: Long? = null
    ): UUID {
        val planId = UUID.randomUUID()
        val dayId = UUID.randomUUID()
        transaction {
            MealPlanTable.insert {
                it[id] = EntityID(planId, MealPlanTable)
                it[user_id] = EntityID(userId, UserTable)
                it[name] = "Plan"
                it[MealPlanTable.status] = status
                it[preferences] = "{}"
                it[created_at] = createdAt
                it[updated_at] = createdAt
                it[MealPlanTable.deleted_at] = deletedAt
                it[server_updated_at] = Instant.fromEpochMilliseconds(createdAt)
            }
            MealPlanDayTable.insert {
                it[id] = EntityID(dayId, MealPlanDayTable)
                it[meal_plan_id] = EntityID(planId, MealPlanTable)
                it[day_index] = 0
                it[dinner_recipe_id] = EntityID(dinnerRecipeId, RecipeTable)
                it[lunch_recipe_id] = null
            }
        }
        return planId
    }
}
