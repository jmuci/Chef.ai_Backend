package com.tenmilelabs.application.dto

import com.tenmilelabs.domain.model.Label
import kotlinx.serialization.Serializable

@Serializable
data class CreateRecipeRequest (
    val title: String,
    val label: Label,
    val description: String,
    val preparationTimeMinutes: Int,
    val recipeUrl: String,
    val imageUrl: String,
)