package com.tenmilelabs.infrastructure.extraction

import com.tenmilelabs.domain.extraction.IngredientParser
import com.tenmilelabs.domain.extraction.IsoDurationParser
import com.tenmilelabs.domain.model.ExtractedRecipe
import com.tenmilelabs.domain.model.RecipeStep
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MicrodataExtractor {

    fun extract(document: Document, sourceUrl: String): ExtractedRecipe? {
        val recipeElement = document.selectFirst("[itemtype*=schema.org/Recipe]") ?: return null

        val title = itemprop(recipeElement, "name") ?: return null
        val description = itemprop(recipeElement, "description")

        val servings = itemprop(recipeElement, "recipeYield")
        val prepTime = IsoDurationParser.parseToMinutes(
            itempropAttr(recipeElement, "prepTime", "datetime")
                ?: itempropAttr(recipeElement, "prepTime", "content")
                ?: itemprop(recipeElement, "prepTime")
        )
        val cookTime = IsoDurationParser.parseToMinutes(
            itempropAttr(recipeElement, "cookTime", "datetime")
                ?: itempropAttr(recipeElement, "cookTime", "content")
                ?: itemprop(recipeElement, "cookTime")
        )
        val totalTime = IsoDurationParser.parseToMinutes(
            itempropAttr(recipeElement, "totalTime", "datetime")
                ?: itempropAttr(recipeElement, "totalTime", "content")
                ?: itemprop(recipeElement, "totalTime")
        )

        val ingredients = recipeElement
            .select("[itemprop=recipeIngredient], [itemprop=ingredients]")
            .map { IngredientParser.parse(it.text().trim()) }

        val steps = parseSteps(recipeElement)

        val tags = recipeElement
            .select("[itemprop=recipeCategory]")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }

        return ExtractedRecipe(
            title = title,
            description = description,
            servings = servings,
            prepTimeMinutes = prepTime,
            cookTimeMinutes = cookTime,
            totalTimeMinutes = totalTime,
            ingredients = ingredients,
            steps = steps,
            tags = tags,
            yieldAmount = servings,
            sourceUrl = sourceUrl
        )
    }

    private fun parseSteps(recipeElement: Element): List<RecipeStep> {
        // Try itemprop="recipeInstructions" container with nested steps
        val instructionElements = recipeElement.select("[itemprop=recipeInstructions]")
        if (instructionElements.isEmpty()) return emptyList()

        val steps = mutableListOf<RecipeStep>()
        var order = 1

        for (el in instructionElements) {
            // Check for nested step items
            val stepElements = el.select("[itemprop=step], [itemprop=text], [itemtype*=HowToStep]")
            if (stepElements.isNotEmpty()) {
                for (step in stepElements) {
                    val text = step.text().trim()
                    if (text.isNotBlank()) {
                        steps.add(RecipeStep(order = order++, text = text))
                    }
                }
            } else {
                // The element itself contains the instruction text
                val text = el.text().trim()
                if (text.isNotBlank()) {
                    steps.add(RecipeStep(order = order++, text = text))
                }
            }
        }
        return steps
    }

    private fun itemprop(root: Element, prop: String): String? {
        val el = root.selectFirst("[itemprop=$prop]") ?: return null
        // Prefer content attribute (meta tags), then text
        return el.attr("content").ifBlank { null } ?: el.text().trim().ifEmpty { null }
    }

    private fun itempropAttr(root: Element, prop: String, attr: String): String? {
        val el = root.selectFirst("[itemprop=$prop]") ?: return null
        return el.attr(attr).ifBlank { null }
    }
}
