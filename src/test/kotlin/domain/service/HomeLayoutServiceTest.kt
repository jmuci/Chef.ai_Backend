package com.tenmilelabs.domain.service

import com.tenmilelabs.application.dto.*
import io.ktor.util.logging.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HomeLayoutServiceTest {

    private val log = KtorSimpleLogger("HomeLayoutServiceIntegrationTest")

    @Test
    fun `unknown component types are ignored without crashing`() {
        val service = HomeLayoutService(
            loadLayoutJson = { layoutWithUnknownTypes },
            log = log
        )

        val layout = service.getHomeLayout()

        assertEquals("1.0.0", layout.schemaVersion)
        assertEquals(2, layout.components.size)
        assertTrue(layout.components.first() is SectionHeaderComponent)

        val carousel = layout.components[1] as CarouselComponent
        assertEquals(1, carousel.items.size)
        assertTrue(carousel.items.first() is LargeCardComponent)
    }

    @Test
    fun `checksum generation is deterministic`() {
        val json = """[{"id":"one","type":"section_header","title":"For You"}]"""
        val checksumA = HomeLayoutService.computeLayoutChecksum(json)
        val checksumB = HomeLayoutService.computeLayoutChecksum(json)
        assertEquals(checksumA, checksumB)
    }

    @Test
    fun `sidecar is non-null and contains at least one recipe`() {
        val service = HomeLayoutService(log = log)
        val layout = service.getHomeLayout()

        val sidecar = layout.sidecar
        assertNotNull(sidecar)
        assertTrue(sidecar.recipes.isNotEmpty(), "sidecar must have at least one recipe")
        assertTrue(sidecar.creators.isNotEmpty(), "sidecar must have at least one creator")
    }

    @Test
    fun `every recipeId in components has a matching sidecar recipe`() {
        val service = HomeLayoutService(log = log)
        val layout = service.getHomeLayout()

        val sidecar = assertNotNull(layout.sidecar)
        val sidecarUuids = sidecar.recipes.map { it.uuid }.toSet()

        val layoutRecipeIds = collectRecipeIds(layout.components)
        assertTrue(layoutRecipeIds.isNotEmpty(), "layout must contain at least one recipeId")

        for (id in layoutRecipeIds) {
            assertTrue(id in sidecarUuids, "recipeId '$id' in components has no matching sidecar recipe")
        }
    }

    @Test
    fun `every tagId, labelId, and creatorId in sidecar recipes resolves within the sidecar`() {
        val service = HomeLayoutService(log = log)
        val layout = service.getHomeLayout()

        val sidecar = assertNotNull(layout.sidecar)
        val tagUuids = sidecar.tags.map { it.uuid }.toSet()
        val labelUuids = sidecar.labels.map { it.uuid }.toSet()
        val creatorUuids = sidecar.creators.map { it.uuid }.toSet()

        for (recipe in sidecar.recipes) {
            assertTrue(recipe.creatorId in creatorUuids,
                "recipe '${recipe.uuid}' creatorId '${recipe.creatorId}' not in sidecar.creators")
            for (tagId in recipe.tagIds) {
                assertTrue(tagId in tagUuids,
                    "recipe '${recipe.uuid}' tagId '$tagId' not in sidecar.tags")
            }
            for (labelId in recipe.labelIds) {
                assertTrue(labelId in labelUuids,
                    "recipe '${recipe.uuid}' labelId '$labelId' not in sidecar.labels")
            }
        }
    }

    @Test
    fun `every sidecar recipe has at least one ingredient and one step`() {
        val service = HomeLayoutService(log = log)
        val layout = service.getHomeLayout()

        val sidecar = assertNotNull(layout.sidecar)
        for (recipe in sidecar.recipes) {
            assertTrue(recipe.ingredients.isNotEmpty(),
                "recipe '${recipe.uuid}' has no ingredients")
            assertTrue(recipe.steps.isNotEmpty(),
                "recipe '${recipe.uuid}' has no steps")
        }
    }

    @Test
    fun `checksum covers only components, not the sidecar`() {
        val service = HomeLayoutService(log = log)
        val layout = service.getHomeLayout()

        // Recompute checksum using only components — must match the returned checksum
        val serviceJson = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            explicitNulls = true
            encodeDefaults = false
        }
        val componentsJson = serviceJson.encodeToString(layout.components)
        val expectedChecksum = HomeLayoutService.computeLayoutChecksum(componentsJson)
        assertEquals(expectedChecksum, layout.layoutChecksum,
            "layoutChecksum must be derived from components only, not the sidecar")
    }

    private fun collectRecipeIds(components: List<HomeComponent>): Set<String> {
        val ids = mutableSetOf<String>()
        for (component in components) {
            when (component) {
                is LargeCardComponent -> component.recipeId?.let { ids.add(it) }
                is SquaredCardComponent -> component.recipeId?.let { ids.add(it) }
                is ListCardComponent -> component.recipeId?.let { ids.add(it) }
                is CarouselComponent -> ids.addAll(collectRecipeIds(component.items))
                else -> {}
            }
        }
        return ids
    }

    private companion object {
        val layoutWithUnknownTypes = """
            {
              "schemaVersion": "1.0.0",
              "layoutChecksum": "ignored",
              "components": [
                {
                  "type": "section_header",
                  "id": "header-for-you",
                  "title": "For You"
                },
                {
                  "type": "unsupported_top_level",
                  "id": "drop-me",
                  "title": "Ignore"
                },
                {
                  "type": "carousel",
                  "id": "carousel-for-you",
                  "items": [
                    {
                      "type": "large_card",
                      "id": "large-1",
                      "recipeId": "a1b2c3d4-0000-0000-0000-000000000001"
                    },
                    {
                      "type": "section_header",
                      "id": "nested-header",
                      "title": "Invalid item type"
                    },
                    {
                      "type": "unsupported_nested",
                      "id": "drop-nested"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
    }
}
