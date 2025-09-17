package com.tenmilelabs.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateRecipeRequest(
    val title: String,
    val label: String,
    val description: String,
    val prepTimeMins: String,
    val recipeUrl: String,
    val imageUrl: String,
    val imageUrlThumbnail: String = "",
)

@Serializable
data class RecipeResponse(
    val uuid: String,
)
