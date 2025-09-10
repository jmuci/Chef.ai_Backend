package com.tenmilelabs.domain.service

import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.application.dto.toRecipe
import com.tenmilelabs.domain.model.Label
import com.tenmilelabs.domain.model.Recipe
import com.tenmilelabs.infrastructure.database.RecipesRepository
import io.ktor.util.logging.*
import java.util.*

class RecipesService(private val recipesRepository: RecipesRepository, private val log: Logger) {

    fun getAllRecipes(): List<Recipe> = recipesRepository.allRecipes()

    fun getRecipesByLabel(label: Label): List<Recipe> =
        recipesRepository.recipesByLabel(label = label)

    fun createRecipe(request: CreateRecipeRequest): Recipe? {
        try {
            val recipe = request.toRecipe(UUID.randomUUID().toString())
            recipesRepository.addRecipe(recipe)
            return recipe
        } catch (ex: IllegalArgumentException) {
            log.error("400: Failed to create recipe for $request. Some parameter is missing or malformed! ${request.label} ${request.preparationTimeMinutes}", ex)
        }
        return null
    }
}