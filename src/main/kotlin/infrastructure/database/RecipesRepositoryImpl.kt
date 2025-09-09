package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.domain.model.Label
import com.tenmilelabs.domain.model.Recipe

// Repository Singleton
object RecipesRepositoryImpl : RecipesRepository {
    val testRecipe1 = Recipe(uuid = "1", title = "Recipe 1", description = "Recipe Description 1", label = Label.Mediterranean, recipeUrl = "http://example.com/recipe", imageUrl = "http://example.com/image.jpg", imageUrlThumbnail = "http://example.com/thumb.jpg", preparationTimeMinutes = 30)
    val testRecipe2 = Recipe(uuid = "2", title = "Recipe 2", description = "Recipe Description 2", label = Label.Mediterranean, recipeUrl = "http://example.com/recipe", imageUrl = "http://example.com/image.jpg", imageUrlThumbnail = "http://example.com/thumb.jpg", preparationTimeMinutes = 40)
    val testRecipe3 = Recipe(uuid = "3", title = "Recipe 3", description = "Recipe Description 3", label = Label.Vegetarian, recipeUrl = "http://example.com/recipe", imageUrl = "http://example.com/image.jpg", imageUrlThumbnail = "http://example.com/thumb.jpg", preparationTimeMinutes = 50)
    val testRecipe4 = Recipe(uuid = "4", title = "Recipe 4", description = "Recipe Description 4", label = Label.LowCarb, recipeUrl = "http://example.com/recipe", imageUrl = "http://example.com/image.jpg", imageUrlThumbnail = "http://example.com/thumb.jpg", preparationTimeMinutes = 60)
    val testRecipe5 = Recipe(uuid = "5", title = "Recipe 5", description = "Recipe Description 5", label = Label.LowCarb, recipeUrl = "http://example.com/recipe", imageUrl = "http://example.com/image.jpg", imageUrlThumbnail = "http://example.com/thumb.jpg", preparationTimeMinutes = 60)

    private val recipes = mutableListOf(testRecipe1, testRecipe2, testRecipe3, testRecipe4, testRecipe5)

    override fun allRecipes(): List<Recipe> = recipes

    override fun recipesByLabel(label: Label): List<Recipe> = recipes.filter { it.label == label }

    override fun recipeByTitle(title: String) = recipes.find { it.title.equals(title, ignoreCase = true) }

    override fun recipeById(id: String) = recipes.find { it.uuid == id }

    override fun addRecipe(recipe: Recipe) {
        if (recipeByTitle(recipe.title) != null) {
            throw IllegalStateException("Cannot duplicate recipe titles!")
        }
        recipes.add(recipe)
    }

    override fun removeRecipe(uuid: String) : Boolean = recipes.removeIf { it.uuid == uuid }
}