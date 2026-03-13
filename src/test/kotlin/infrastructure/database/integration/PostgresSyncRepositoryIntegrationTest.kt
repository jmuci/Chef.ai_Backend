package com.tenmilelabs.infrastructure.database.integration

import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.application.dto.SyncRecipeIngredient
import com.tenmilelabs.application.dto.SyncRecipeStep
import com.tenmilelabs.infrastructure.database.initDatabaseAndSchema
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresSyncRepository
import com.tenmilelabs.infrastructure.database.tables.AllergenTable
import com.tenmilelabs.infrastructure.database.tables.IngredientTable
import com.tenmilelabs.infrastructure.database.tables.LabelTable
import com.tenmilelabs.infrastructure.database.tables.SourceClassificationTable
import com.tenmilelabs.infrastructure.database.tables.TagTable
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Tag("db-integration")
class PostgresSyncRepositoryIntegrationTest {

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
}
