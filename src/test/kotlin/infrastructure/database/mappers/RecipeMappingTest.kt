package com.tenmilelabs.infrastructure.database.mappers

import com.tenmilelabs.domain.model.Privacy
import com.tenmilelabs.infrastructure.database.dao.RecipeDAO
import com.tenmilelabs.infrastructure.database.tables.AllergenTable
import com.tenmilelabs.infrastructure.database.tables.IngredientTable
import com.tenmilelabs.infrastructure.database.tables.LabelTable
import com.tenmilelabs.infrastructure.database.tables.RecipeIngredientTable
import com.tenmilelabs.infrastructure.database.tables.RecipeLabelTable
import com.tenmilelabs.infrastructure.database.tables.RecipeTable
import com.tenmilelabs.infrastructure.database.tables.RecipeTagTable
import com.tenmilelabs.infrastructure.database.tables.TagTable
import com.tenmilelabs.infrastructure.database.tables.UserTable
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecipeMappingTest {
    @BeforeEach
    fun setupDb() {
        Database.connect(
            url = "jdbc:h2:mem:recipe_mapping_${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
            driver = "org.h2.Driver"
        )

        transaction {
            SchemaUtils.create(
                AllergenTable,
                IngredientTable,
                TagTable,
                LabelTable,
                UserTable,
                RecipeTable,
                RecipeIngredientTable,
                RecipeTagTable,
                RecipeLabelTable
            )
        }
    }

    @Test
    fun `daoToModel maps recipe dao fields`() {
        val creatorUuid = UUID.randomUUID()
        val now = stableInstant()

        val mapped = transaction {
            insertUser(creatorUuid)
            val dao = RecipeDAO.new {
                title = "Mapped Recipe"
                description = "Description"
                imageUrl = "https://example.com/image.jpg"
                imageUrlThumbnail = "https://example.com/thumb.jpg"
                prepTimeMinutes = 10
                cookTimeMinutes = 20
                servings = 2
                privacy = "PUBLIC"
                this.creatorId = EntityID(creatorUuid, UserTable)
                recipeExternalUrl = "https://example.com"
                updatedAt = 1234L
                deletedAt = null
                serverUpdatedAt = now
            }
            daoToModel(dao)
        }

        assertEquals("Mapped Recipe", mapped.title)
        assertEquals(Privacy.PUBLIC, mapped.privacy)
        assertEquals(creatorUuid.toString(), mapped.creatorId)
        assertEquals("https://example.com", mapped.recipeExternalUrl)
        assertEquals(1234L, mapped.updatedAt)
        assertNull(mapped.deletedAt)
        assertEquals(now.toString(), mapped.serverUpdatedAt)
    }

    @Test
    fun `toRecipeIngredient maps relation row`() {
        val recipeId = UUID.randomUUID()
        val ingredientId = UUID.randomUUID()
        val serverUpdatedAt = stableInstant()

        val mapped = transaction {
            insertRecipe(recipeId)
            insertIngredient(ingredientId)

            RecipeIngredientTable.insert {
                it[RecipeIngredientTable.recipeId] = EntityID(recipeId, RecipeTable)
                it[RecipeIngredientTable.ingredientId] = EntityID(ingredientId, IngredientTable)
                it[quantity] = 42.0
                it[unit] = "g"
                it[updatedAt] = 111L
                it[deletedAt] = null
                it[RecipeIngredientTable.serverUpdatedAt] = serverUpdatedAt
            }

            RecipeIngredientTable.selectAll().first().toRecipeIngredient()
        }

        assertEquals(recipeId, mapped.recipeId)
        assertEquals(ingredientId, mapped.ingredientId)
        assertEquals(42.0, mapped.quantity)
        assertEquals("g", mapped.unit)
        assertEquals(111L, mapped.updatedAt)
        assertEquals(serverUpdatedAt.toString(), mapped.serverUpdatedAt)
    }

    @Test
    fun `toRecipeTag maps relation row`() {
        val recipeId = UUID.randomUUID()
        val tagId = UUID.randomUUID()
        val serverUpdatedAt = stableInstant()

        val mapped = transaction {
            insertRecipe(recipeId)
            insertTag(tagId)

            RecipeTagTable.insert {
                it[RecipeTagTable.recipeId] = EntityID(recipeId, RecipeTable)
                it[RecipeTagTable.tagId] = EntityID(tagId, TagTable)
                it[updatedAt] = 222L
                it[deletedAt] = null
                it[RecipeTagTable.serverUpdatedAt] = serverUpdatedAt
            }

            RecipeTagTable.selectAll().first().toRecipeTag()
        }

        assertEquals(recipeId, mapped.recipeId)
        assertEquals(tagId, mapped.tagId)
        assertEquals(222L, mapped.updatedAt)
        assertEquals(serverUpdatedAt.toString(), mapped.serverUpdatedAt)
    }

    @Test
    fun `toRecipeLabel maps relation row`() {
        val recipeId = UUID.randomUUID()
        val labelId = UUID.randomUUID()
        val serverUpdatedAt = stableInstant()

        val mapped = transaction {
            insertRecipe(recipeId)
            insertLabel(labelId)

            RecipeLabelTable.insert {
                it[RecipeLabelTable.recipeId] = EntityID(recipeId, RecipeTable)
                it[RecipeLabelTable.labelId] = EntityID(labelId, LabelTable)
                it[updatedAt] = 333L
                it[deletedAt] = null
                it[RecipeLabelTable.serverUpdatedAt] = serverUpdatedAt
            }

            RecipeLabelTable.selectAll().first().toRecipeLabel()
        }

        assertEquals(recipeId, mapped.recipeId)
        assertEquals(labelId, mapped.labelId)
        assertEquals(333L, mapped.updatedAt)
        assertEquals(serverUpdatedAt.toString(), mapped.serverUpdatedAt)
    }

    private fun insertRecipe(recipeId: UUID) {
        val creatorId = UUID.randomUUID()
        insertUser(creatorId)

        RecipeTable.insert {
            it[id] = EntityID(recipeId, RecipeTable)
            it[title] = "Recipe"
            it[description] = "Description"
            it[image_url] = "https://example.com/image.jpg"
            it[image_url_thumbnail] = "https://example.com/thumb.jpg"
            it[prep_time_minutes] = 10
            it[cook_time_minutes] = 20
            it[servings] = 2
            it[creator_id] = EntityID(creatorId, UserTable)
            it[recipe_external_url] = null
            it[privacy] = "PRIVATE"
            it[updated_at] = 1L
            it[deleted_at] = null
            it[server_updated_at] = Clock.System.now()
        }
    }

    private fun insertIngredient(ingredientId: UUID) {
        IngredientTable.insert {
            it[id] = EntityID(ingredientId, IngredientTable)
            it[display_name] = "Ingredient"
            it[allergen_id] = null
            it[source_primary_id] = null
            it[updated_at] = 1L
            it[deleted_at] = null
            it[server_updated_at] = Clock.System.now()
        }
    }

    private fun insertTag(tagId: UUID) {
        TagTable.insert {
            it[id] = EntityID(tagId, TagTable)
            it[display_name] = "Tag"
            it[updated_at] = 1L
            it[deleted_at] = null
            it[server_updated_at] = Clock.System.now()
        }
    }

    private fun insertLabel(labelId: UUID) {
        LabelTable.insert {
            it[id] = EntityID(labelId, LabelTable)
            it[display_name] = "Label"
            it[updated_at] = 1L
            it[deleted_at] = null
            it[server_updated_at] = Clock.System.now()
        }
    }

    private fun insertUser(userId: UUID) {
        UserTable.insert {
            it[id] = EntityID(userId, UserTable)
            it[user_name] = "user_$userId"
            it[email] = "user_$userId@example.com"
            it[password_hash] = "hash"
        }
    }

    private fun stableInstant(): Instant =
        Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
}
