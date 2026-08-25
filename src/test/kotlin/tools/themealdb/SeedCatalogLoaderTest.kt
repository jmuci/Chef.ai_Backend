package com.tenmilelabs.tools.themealdb

import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeedCatalogLoaderTest {

    private fun writeSeedFile(sql: String): File {
        val file = Files.createTempFile("seed-catalog-test", ".sql").toFile()
        file.writeText(sql)
        file.deleteOnExit()
        return file
    }

    @Test
    fun parsesIngredientsTagsAndLabelsButNotRecipeChildTables() {
        val ingredientId = UUID.randomUUID()
        val tagId = UUID.randomUUID()
        val labelId = UUID.randomUUID()
        val decoyRecipeLabelRowId = UUID.randomUUID() // must NOT be picked up as a "labels" row

        val sql = """
            INSERT INTO ingredients (uuid, display_name, updated_at, deleted_at, server_updated_at) VALUES
              ('$ingredientId', 'Chicken Breast', 1, NULL, now());

            INSERT INTO tags (uuid, display_name, updated_at, deleted_at, server_updated_at) VALUES
              ('$tagId', 'Spicy', 1, NULL, now());

            INSERT INTO labels (uuid, display_name, updated_at, deleted_at, server_updated_at) VALUES
              ('$labelId', 'Vegan', 1, NULL, now());

            INSERT INTO recipe_labels (recipe_id, label_id, updated_at, deleted_at, server_updated_at) VALUES
              ('$decoyRecipeLabelRowId', '$labelId', 1, NULL, now());
        """.trimIndent()

        val catalog = SeedCatalogLoader.load(writeSeedFile(sql))

        assertEquals(mapOf("chicken breast" to ingredientId), catalog.ingredientIdsByNormalizedName)
        assertEquals(mapOf("spicy" to tagId), catalog.tagIdsByNormalizedName)
        assertEquals(mapOf("vegan" to labelId), catalog.labelIdsByNormalizedName)
    }

    @Test
    fun missingTablesYieldEmptyMaps() {
        val catalog = SeedCatalogLoader.load(writeSeedFile("-- nothing here"))
        assertEquals(emptyMap(), catalog.ingredientIdsByNormalizedName)
        assertNull(catalog.ingredientIdsByNormalizedName["anything"])
    }

    @Test
    fun `a semicolon inside a line comment does not truncate the statement`() {
        // Regression: the statement regex is non-greedy up to the first `;`, so an unstripped
        // comment semicolon hid every row after it. The importer then minted duplicate catalog
        // rows under fresh UUIDs instead of reusing the curated ones. See jmuci/ChefAI#182.
        val firstId = UUID.randomUUID()
        val afterCommentId = UUID.randomUUID()

        val sql = """
            INSERT INTO tags (uuid, display_name, updated_at, deleted_at, server_updated_at) VALUES
              ('$firstId', 'Spicy', 1, NULL, now()),
              -- a comment; with a semicolon in it
              ('$afterCommentId', 'Kid-Friendly', 2, NULL, now());
        """.trimIndent()

        val catalog = SeedCatalogLoader.load(writeSeedFile(sql))

        assertEquals(firstId, catalog.tagIdsByNormalizedName["spicy"])
        assertEquals(
            afterCommentId,
            catalog.tagIdsByNormalizedName["kid-friendly"],
            "rows after a comment containing `;` must still load"
        )
    }

    @Test
    fun `every browse-category tag in the real seed file is resolvable`() {
        // The importer resolves MealTypeClassifier's output against this catalog by name. Any name
        // that fails to resolve here is silently minted as a second, duplicate tag at import time.
        val seedFile = File("src/main/resources/sql/seed.sql")
        assertTrue(seedFile.exists(), "expected ${seedFile.absolutePath} to exist")

        val catalog = SeedCatalogLoader.load(seedFile)
        val browseTags = listOf(
            "breakfast", "lunch", "dinner", "snack", "dessert", "quick",
            "kid-friendly", "comfort food", "budget", "healthy", "sandwich", "air fryer"
        )
        val missing = browseTags.filter { it !in catalog.tagIdsByNormalizedName }
        assertTrue(missing.isEmpty(), "browse tags missing from seed.sql catalog: $missing")

        val browseLabels = listOf("high protein", "low carb")
        val missingLabels = browseLabels.filter { it !in catalog.labelIdsByNormalizedName }
        assertTrue(missingLabels.isEmpty(), "browse labels missing from seed.sql catalog: $missingLabels")
    }
}
