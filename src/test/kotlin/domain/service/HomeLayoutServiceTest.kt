package com.tenmilelabs.domain.service

import com.tenmilelabs.application.dto.CarouselComponent
import com.tenmilelabs.application.dto.LargeCardComponent
import com.tenmilelabs.application.dto.SectionHeaderComponent
import io.ktor.util.logging.KtorSimpleLogger
import kotlin.test.Test
import kotlin.test.assertEquals
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
                      "title": "Valencian Paella"
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
