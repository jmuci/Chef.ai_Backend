package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.domain.model.Label
import com.tenmilelabs.domain.model.Recipe

enum class FilterFields(val label: String) {
    BY_ID("uuid"),
    BY_TITLE("title"),
}

// Repository Singleton
interface RecipesRepository {

    suspend fun allRecipes(): List<Recipe>

    suspend fun recipesByLabel(label: Label): List<Recipe>

    suspend fun recipeByTitle(title: String): Recipe?

    suspend fun recipeById(id: String): Recipe?

    suspend fun addRecipe(recipeRequest: CreateRecipeRequest): String

    suspend fun removeRecipe(uuid: String): Boolean
}