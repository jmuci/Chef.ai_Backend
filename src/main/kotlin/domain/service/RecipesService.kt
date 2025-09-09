package com.tenmilelabs.domain.service

import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.domain.model.Label
import com.tenmilelabs.domain.model.Recipe
import com.tenmilelabs.infrastructure.database.RecipesRepository
import java.util.UUID

class RecipesService(private val recipesRepository: RecipesRepository) {

    fun getAllRecipes(): List<Recipe> = recipesRepository.allRecipes()

    fun getRecipesByLabel(label: Label): List<Recipe> =
        recipesRepository.recipesByLabel(label = label)

    fun createRecipe(request: CreateRecipeRequest): Recipe {
        val recipe = Recipe(
            uuid = UUID.randomUUID().toString(),
            title = request.title,
            description = request.description,
            label = request.label,
            preparationTimeMinutes = request.preparationTimeMinutes,
            imageUrlThumbnail = "",
            imageUrl = request.imageUrl,
            recipeUrl = request.recipeUrl,
        )
        recipesRepository.addRecipe(recipe)
        return recipe
    }
}