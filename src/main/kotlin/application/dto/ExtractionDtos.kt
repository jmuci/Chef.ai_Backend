package com.tenmilelabs.application.dto

import com.tenmilelabs.domain.model.ExtractedRecipe
import com.tenmilelabs.domain.model.ParsedIngredient
import kotlinx.serialization.Serializable

@Serializable
data class ExtractRecipeRequest(
    val url: String
)

@Serializable
data class RecipeExtractionResponse(
    val title: String,
    val description: String?,
    val servings: String?,
    val prepTimeMinutes: Int?,
    val cookTimeMinutes: Int?,
    val totalTimeMinutes: Int?,
    val ingredients: List<IngredientDto>,
    val steps: List<StepDto>,
    val tags: List<String>,
    val yieldAmount: String?,
    val sourceUrl: String
)

@Serializable
data class IngredientDto(
    val raw: String,
    val quantity: String?,
    val unit: String?,
    val name: String,
    val preparation: String?
)

@Serializable
data class StepDto(
    val order: Int,
    val text: String
)

fun ExtractedRecipe.toResponse(): RecipeExtractionResponse = RecipeExtractionResponse(
    title = title,
    description = description,
    servings = servings,
    prepTimeMinutes = prepTimeMinutes,
    cookTimeMinutes = cookTimeMinutes,
    totalTimeMinutes = totalTimeMinutes,
    ingredients = ingredients.map { it.toDto() },
    steps = steps.map { StepDto(order = it.order, text = it.text) },
    tags = tags,
    yieldAmount = yieldAmount,
    sourceUrl = sourceUrl
)

private fun ParsedIngredient.toDto(): IngredientDto = IngredientDto(
    raw = raw,
    quantity = quantity,
    unit = unit,
    name = name,
    preparation = preparation
)
