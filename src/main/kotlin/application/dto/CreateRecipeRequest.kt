package com.tenmilelabs.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateRecipeRequest(
    val title: String,
    val description: String,
    val imageUrl: String,
    val imageUrlThumbnail: String = "",
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val servings: Int,
    val recipeExternalUrl: String? = null,
    val privacy: String
)

@Serializable
data class RecipeResponse(
    val uuid: String,
)
