package com.tenmilelabs.tools.themealdb

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeedSqlWriterTest {

    @Test
    fun everyGeneratedInsertUsesOnConflictDoNothing() {
        val recipe = MappedRecipe(
            id = UUID.randomUUID(),
            title = "O'Brien's Stew", // apostrophe on purpose, to exercise SQL escaping
            description = "desc",
            imageUrl = "https://example.com/i.jpg",
            imageUrlThumbnail = "https://example.com/t.jpg",
            prepTimeMinutes = 10,
            cookTimeMinutes = 20,
            servings = 4,
            creatorId = TheMealDbMapper.SYSTEM_CREATOR_ID,
            recipeExternalUrl = "https://example.com/source",
            updatedAtMillis = 1_700_000_000_000L,
            steps = listOf(MappedStep(UUID.randomUUID(), 1, "Step one")),
            ingredients = listOf(MappedRecipeIngredient(UUID.randomUUID(), 1.5, "cup")),
            tagIds = listOf(UUID.randomUUID()),
            labelIds = listOf(UUID.randomUUID())
        )
        val newIngredient = MappedIngredient(UUID.randomUUID(), "Cumin", isNew = true)
        val result = MappingResult(
            recipes = listOf(recipe),
            newIngredients = listOf(newIngredient),
            newTags = emptyList(),
            newLabels = emptyList()
        )

        val sql = SeedSqlWriter.write(result, importedAtMillis = 1_700_000_000_000L)

        val insertStatementCount = Regex("INSERT INTO").findAll(sql).count()
        val onConflictCount = Regex("ON CONFLICT").findAll(sql).count()
        assertTrue(insertStatementCount > 0)
        assertEquals(insertStatementCount, onConflictCount)

        // Recipe privacy is always uppercase — the exact bug being fixed in existing seed data.
        assertTrue(sql.contains("'PUBLIC'"))
        assertFalse(sql.contains("'public'"))

        // Apostrophe in the title must be escaped, not break the statement.
        assertTrue(sql.contains("O''Brien''s Stew"))
    }

    @Test
    fun emptyResultProducesOnlySystemUserInsert() {
        val sql = SeedSqlWriter.write(
            MappingResult(recipes = emptyList(), newIngredients = emptyList(), newTags = emptyList(), newLabels = emptyList()),
            importedAtMillis = 1L
        )
        val insertStatementCount = Regex("INSERT INTO").findAll(sql).count()
        assertEquals(1, insertStatementCount) // just the system user
        assertTrue(sql.contains(TheMealDbMapper.SYSTEM_CREATOR_EMAIL))
    }
}
