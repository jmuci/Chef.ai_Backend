package com.tenmilelabs.model
enum class Label() {
    Vegetarian,
    Pescatarian,
    Mediterranean,
    LowCarb
}

/**
 * enum class Label(string: String) {
 *     Vegetarian("Vegetarian"),
 *     Pescatarian("Pescatarian"),
 *     Mediterranean("Mediterranean"),
 *     LowCarb("Low-Carb")
 * }
 */
data class Recipe (
    val uuid: String,
    val title: String,
    val label: Label, //TODO there should be a table for labels
    val description: String,
    val preparationTimeMinutes: Int,
    val recipeUrl: String,
    val imageUrl: String,
    val imageUrlThumbnail: String
)

data class RecipeList (
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