package com.tenmilelabs.domain.model

import kotlinx.serialization.Serializable

enum class Label() {
    Vegetarian,
    Vegan,
    Pescatarian,
    Mediterranean,
    LowCarb,
    Spanish,
    American,
    French,
    Italian,
    Caribbean,
    NewAmerican,
}

@Serializable
data class Recipe(
    val uuid: String,
    val title: String,
    val label: Label, //TODO there should be a table for labels
    val description: String,

    val prepTimeMins: Int,
    val recipeUrl: String,
    val imageUrl: String,
    val imageUrlThumbnail: String,
    val createdAt: String = "",
    val userId: String = "", // Owner of the recipe
    val isPublic: Boolean = false // Whether recipe is publicly accessible
)

data class RecipeList(
    val recipes: List<Recipe>
)

// TODO Move to some API package for recipes endpoint
fun Recipe.recipeAsRow() = """
    <tr>
        <td>$title</td><td>$description</td><td>$label</td><td>$recipeUrl</td>
    </tr>
    """.trimIndent()

fun List<Recipe>.recipesAsTable() = this.joinToString(
    prefix = "<table rules=\"all\">",
    postfix = "</table>",
    separator = "\n",
    transform = Recipe::recipeAsRow
)