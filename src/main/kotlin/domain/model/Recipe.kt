package com.tenmilelabs.domain.model

import kotlinx.serialization.Serializable

enum class Privacy {
    PUBLIC,
    PRIVATE
}

@Serializable
data class Recipe(
    val uuid: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val imageUrlThumbnail: String,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val servings: Int,
    val creatorId: String,
    val recipeExternalUrl: String? = null,
    val privacy: Privacy,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val serverUpdatedAt: String // Timestamp as ISO string
)

data class RecipeList(
    val recipes: List<Recipe>
)

// TODO Move to some API package for recipes endpoint
fun Recipe.recipeAsRow() = """
    <tr>
        <td>$title</td><td>$description</td><td>$imageUrlThumbnail</td><td>$recipeExternalUrl</td>
    </tr>
    """.trimIndent()

fun List<Recipe>.recipesAsTable() = this.joinToString(
    prefix = "<table rules=\"all\">",
    postfix = "</table>",
    separator = "\n",
    transform = Recipe::recipeAsRow
)