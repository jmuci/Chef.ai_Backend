package com.tenmilelabs.domain.service

import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.application.dto.RecipeResponse
import com.tenmilelabs.domain.model.Label
import com.tenmilelabs.domain.model.Recipe
import com.tenmilelabs.infrastructure.database.RecipesRepository
import io.ktor.util.logging.*

class RecipesService(private val recipesRepository: RecipesRepository, private val log: Logger) {

    suspend fun getAllRecipes(): List<Recipe> = recipesRepository.allRecipes()

    suspend fun getRecipesByLabel(label: Label): List<Recipe> =
        recipesRepository.recipesByLabel(label = label)

    suspend fun createRecipe(request: CreateRecipeRequest): RecipeResponse? {
        try {
            return RecipeResponse(recipesRepository.addRecipe(request))
        } catch (ex: IllegalArgumentException) {
            log.error(
                "400: Failed to create recipe for $request. Some parameter is missing or malformed! ${request.label} ${request.prepTimeMins}",
                ex
            )
        }
        return null
    }
}