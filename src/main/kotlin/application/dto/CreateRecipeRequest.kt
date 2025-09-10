package com.tenmilelabs.application.dto

import com.tenmilelabs.domain.model.Label
import com.tenmilelabs.domain.model.Recipe
import kotlinx.serialization.Serializable

@Serializable
data class CreateRecipeRequest(
    val title: String,
    val label: String,
    val description: String,
    val preparationTimeMinutes: String,
    val recipeUrl: String,
    val imageUrl: String,
)

fun CreateRecipeRequest.toRecipe(id: String): Recipe {
    val enumLabel = Label.valueOf(this.label)
    val intPrepTime = preparationTimeMinutes.toInt()
    return Recipe(
        uuid = id,
        title = title,
        description = description,
        label = enumLabel,
        preparationTimeMinutes = intPrepTime,
        recipeUrl = imageUrl,
        imageUrl = imageUrl,
        imageUrlThumbnail = imageUrl
    )

}