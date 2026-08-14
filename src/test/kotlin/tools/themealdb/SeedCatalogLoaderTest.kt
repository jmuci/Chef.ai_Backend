package com.tenmilelabs.tools.themealdb

import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
