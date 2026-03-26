package com.tenmilelabs.infrastructure.extraction

import com.tenmilelabs.domain.extraction.IngredientParser
import com.tenmilelabs.domain.extraction.IsoDurationParser
import com.tenmilelabs.domain.model.ExtractedRecipe
import com.tenmilelabs.domain.model.RecipeStep
import kotlinx.serialization.json.*
import org.jsoup.nodes.Document

class JsonLdExtractor {

    private val json = Json { ignoreUnknownKeys = true }

    fun extract(document: Document, sourceUrl: String): ExtractedRecipe? {
        val scripts = document.select("script[type=application/ld+json]")
        for (script in scripts) {
            val text = script.data().trim()
            if (text.isEmpty()) continue

            val element = try {
                json.parseToJsonElement(text)
            } catch (_: Exception) {
                continue
            }

            val recipeNode = findRecipeNode(element) ?: continue
            return parseRecipeNode(recipeNode, sourceUrl)
        }
        return null
    }

    private fun findRecipeNode(element: JsonElement): JsonObject? {
        return when (element) {
            is JsonObject -> {
                val type = element.typeValue()
                if (type != null && "Recipe" in type) {
                    return element
                }
                // Check @graph array
                element["@graph"]?.let { graph ->
                    if (graph is JsonArray) {
                        for (item in graph) {
                            findRecipeNode(item)?.let { return it }
                        }
                    }
                }
                null
            }
            is JsonArray -> {
                for (item in element) {
                    findRecipeNode(item)?.let { return it }
                }
                null
            }
            else -> null
        }
    }

    private fun JsonObject.typeValue(): String? {
        val typeEl = this["@type"] ?: return null
        return when (typeEl) {
            is JsonPrimitive -> typeEl.contentOrNull
            is JsonArray -> typeEl.joinToString(",") {
                (it as? JsonPrimitive)?.contentOrNull ?: ""
            }
            else -> null
        }
    }

    private fun parseRecipeNode(node: JsonObject, sourceUrl: String): ExtractedRecipe {
        val title = node.string("name") ?: node.string("headline") ?: "Untitled Recipe"
        val description = node.string("description")?.let { cleanHtml(it) }
        val servings = node.string("recipeYield")
            ?: node.stringFromArray("recipeYield")
        val yieldAmount = servings

        val prepTime = IsoDurationParser.parseToMinutes(node.string("prepTime"))
        val cookTime = IsoDurationParser.parseToMinutes(node.string("cookTime"))
        val totalTime = IsoDurationParser.parseToMinutes(node.string("totalTime"))

        val ingredients = parseIngredients(node["recipeIngredient"])
        val steps = parseInstructions(node["recipeInstructions"])
        val tags = parseTags(node)

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
            yieldAmount = yieldAmount,
            sourceUrl = sourceUrl
        )
    }

    private fun parseIngredients(element: JsonElement?): List<com.tenmilelabs.domain.model.ParsedIngredient> {
        if (element == null) return emptyList()
        val rawList = when (element) {
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> listOf(element.content)
            else -> emptyList()
        }
        return rawList.map { IngredientParser.parse(it) }
    }

    private fun parseInstructions(element: JsonElement?): List<RecipeStep> {
        if (element == null) return emptyList()
        return when (element) {
            is JsonArray -> {
                val steps = mutableListOf<RecipeStep>()
                var order = 1
                for (item in element) {
                    when (item) {
                        is JsonPrimitive -> {
                            val text = item.contentOrNull?.let { cleanHtml(it) }
                            if (!text.isNullOrBlank()) {
                                steps.add(RecipeStep(order = order++, text = text))
                            }
                        }
                        is JsonObject -> {
                            val itemType = item.typeValue() ?: ""
                            if ("HowToStep" in itemType) {
                                val text = extractStepText(item)
                                if (text.isNotBlank()) {
                                    steps.add(RecipeStep(order = order++, text = text))
                                }
                            } else if ("HowToSection" in itemType) {
                                // HowToSection contains itemListElement with HowToStep entries
                                val subItems = item["itemListElement"]
                                if (subItems is JsonArray) {
                                    for (sub in subItems) {
                                        if (sub is JsonObject) {
                                            val text = extractStepText(sub)
                                            if (text.isNotBlank()) {
                                                steps.add(RecipeStep(order = order++, text = text))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
                steps
            }
            is JsonPrimitive -> {
                val text = element.contentOrNull?.let { cleanHtml(it) }
                if (!text.isNullOrBlank()) listOf(RecipeStep(order = 1, text = text))
                else emptyList()
            }
            else -> emptyList()
        }
    }

    private fun extractStepText(step: JsonObject): String {
        // Prefer "text" field, fall back to "description", then "name"
        val raw = step.string("text")
            ?: step.string("description")
            ?: step.string("name")
            ?: ""
        return cleanHtml(raw)
    }

    private fun parseTags(node: JsonObject): List<String> {
        val tags = mutableListOf<String>()

        fun addFromElement(element: JsonElement?) {
            when (element) {
                is JsonArray -> element.forEach { item ->
                    (item as? JsonPrimitive)?.contentOrNull?.let { tags.add(it.trim()) }
                }
                is JsonPrimitive -> {
                    // Comma-separated string
                    element.contentOrNull?.split(",")?.forEach { tag ->
                        val trimmed = tag.trim()
                        if (trimmed.isNotEmpty()) tags.add(trimmed)
                    }
                }
                else -> {}
            }
        }

        addFromElement(node["recipeCategory"])
        addFromElement(node["recipeCuisine"])
        addFromElement(node["keywords"])
        return tags.distinct()
    }

    private fun JsonObject.string(key: String): String? {
        val el = this[key] ?: return null
        return when (el) {
            is JsonPrimitive -> el.contentOrNull
            else -> null
        }
    }

    private fun JsonObject.stringFromArray(key: String): String? {
        val el = this[key] ?: return null
        return when (el) {
            is JsonArray -> el.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
            else -> null
        }
    }

    /** Strip HTML tags from a string (some sites embed HTML in JSON-LD text fields) */
    private fun cleanHtml(text: String): String {
        if ('<' !in text) return text.trim()
        return org.jsoup.Jsoup.parse(text).text().trim()
    }
}
