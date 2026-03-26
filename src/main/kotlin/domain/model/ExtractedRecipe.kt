package com.tenmilelabs.domain.model

data class ExtractedRecipe(
    val title: String,
    val description: String?,
    val servings: String?,
    val prepTimeMinutes: Int?,
    val cookTimeMinutes: Int?,
    val totalTimeMinutes: Int?,
    val ingredients: List<ParsedIngredient>,
    val steps: List<RecipeStep>,
    val tags: List<String>,
    val yieldAmount: String?,
    val sourceUrl: String
)

data class ParsedIngredient(
    val raw: String,
    val quantity: String?,
    val unit: String?,
    val name: String,
    val preparation: String?
)

data class RecipeStep(
    val order: Int,
    val text: String
)
