package com.tenmilelabs.domain.service

import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.application.dto.RecipeResponse
import com.tenmilelabs.domain.model.Label
import com.tenmilelabs.domain.model.Recipe
import com.tenmilelabs.domain.repository.RecipesRepository
import io.ktor.util.logging.*
import java.util.UUID

class RecipesService(private val recipesRepository: RecipesRepository, private val log: Logger) {

    suspend fun getAllRecipes(): List<Recipe> = recipesRepository.allRecipes()

    suspend fun getRecipesByUserId(userId: UUID): List<Recipe> =
        recipesRepository.recipesByUserId(userId)

    suspend fun getPublicRecipes(): List<Recipe> =
        recipesRepository.publicRecipes()

    suspend fun getAccessibleRecipes(userId: UUID): List<Recipe> {
        // Get user's own recipes and public recipes
        val userRecipes = recipesRepository.recipesByUserId(userId)
        val publicRecipes = recipesRepository.publicRecipes()
        return (userRecipes + publicRecipes).distinctBy { it.uuid }
    }

    suspend fun getRecipesByLabel(label: Label): List<Recipe> =
        recipesRepository.recipesByLabel(label = label)

    suspend fun getRecipeById(id: String, userId: UUID): Recipe? {
        val recipe = recipesRepository.recipeById(id)
        return if (recipe != null && (recipe.userId == userId.toString() || recipe.isPublic)) {
            recipe
        } else {
            null
        }
    }

    suspend fun createRecipe(request: CreateRecipeRequest, userId: UUID): RecipeResponse? {
        try {
            return RecipeResponse(recipesRepository.addRecipe(request, userId))
        } catch (ex: IllegalArgumentException) {
            log.error(
                "400: Failed to create recipe for $request. Some parameter is missing or malformed! ${request.label} ${request.prepTimeMins}",
                ex
            )
        }
        return null
    }

    suspend fun deleteRecipe(recipeId: String, userId: UUID): Boolean {
        return recipesRepository.removeRecipe(recipeId, userId)
    }
}