package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.domain.model.Label
import com.tenmilelabs.domain.model.Recipe

enum class FilterFields(val label: String) {
    BY_ID("recipeId"),
    BY_TITLE("title"),
}

// Repository Singleton
interface RecipesRepository {

    fun allRecipes(): List<Recipe>

    fun recipesByLabel(label: Label): List<Recipe>

    fun recipeByTitle(title: String): Recipe?

    fun recipeById(id: String): Recipe?

    fun addRecipe(recipe: Recipe)

    fun removeRecipe(uuid: String): Boolean
}