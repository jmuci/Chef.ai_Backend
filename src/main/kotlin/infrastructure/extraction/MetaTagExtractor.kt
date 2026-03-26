package com.tenmilelabs.infrastructure.extraction

import com.tenmilelabs.domain.model.ExtractedRecipe
import org.jsoup.nodes.Document

class MetaTagExtractor {

    fun extract(document: Document, sourceUrl: String): ExtractedRecipe? {
        val title = metaContent(document, "og:title")
            ?: metaContent(document, "twitter:title")
            ?: document.title().takeIf { it.isNotBlank() }
            ?: return null

        val description = metaContent(document, "og:description")
            ?: metaContent(document, "twitter:description")
            ?: metaContent(document, "description")

        // Last-resort: only title and description, no structured recipe data
        return ExtractedRecipe(
            title = title,
            description = description,
            servings = null,
            prepTimeMinutes = null,
            cookTimeMinutes = null,
            totalTimeMinutes = null,
            ingredients = emptyList(),
            steps = emptyList(),
            tags = emptyList(),
            yieldAmount = null,
            sourceUrl = sourceUrl
        )
    }

    private fun metaContent(document: Document, name: String): String? {
        // Try property attribute (Open Graph)
        val byProperty = document.selectFirst("meta[property=$name]")
        if (byProperty != null) {
            return byProperty.attr("content").ifBlank { null }
        }
        // Try name attribute (standard meta, twitter)
        val byName = document.selectFirst("meta[name=$name]")
        if (byName != null) {
            return byName.attr("content").ifBlank { null }
        }
        return null
    }
}
