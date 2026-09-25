package com.tenmilelabs.domain.service

import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.application.dto.RecipeResponse
import com.tenmilelabs.domain.model.Privacy
import com.tenmilelabs.domain.model.Recipe
import com.tenmilelabs.domain.repository.RecipesRepository
import io.ktor.util.logging.*
import java.util.*

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


    suspend fun getRecipeById(id: String, userId: UUID): Recipe? {
        val recipe = recipesRepository.recipeById(id)
        return recipe?.takeIf { it.isVisibleTo(userId) }
    }

    /**
     * Title-scoped counterpart to [getRecipeById], applying the same visibility rule.
     *
     * Note the repository returns the first title match regardless of owner, so a private recipe
     * owned by someone else shadows a same-titled one of the caller's and this returns null rather
     * than the caller's copy. That is the safe direction to fail, and titles are not a stable way
     * to address a recipe anyway — `/api/v1/recipes/search` is the supported lookup.
     */
    suspend fun getRecipeByTitle(title: String, userId: UUID): Recipe? {
        val recipe = recipesRepository.recipeByTitle(title)
        return recipe?.takeIf { it.isVisibleTo(userId) }
    }

    /** A recipe is visible to its creator, or to anyone when it is PUBLIC. */
    private fun Recipe.isVisibleTo(userId: UUID): Boolean =
        creatorId == userId.toString() || privacy == Privacy.PUBLIC

    suspend fun createRecipe(request: CreateRecipeRequest, userId: UUID): RecipeResponse? {
        // Stored verbatim and read back with enumValueOf, so an unknown value would be accepted
        // here and then fail every later read of the caller's recipes. /sync/push applies the same
        // rule (SyncErrors.INVALID_PRIVACY).
        if (Privacy.entries.none { it.name == request.privacy }) {
            log.warn("400: Rejected recipe with invalid privacy '${request.privacy}' for user $userId")
            return null
        }
        try {
            return RecipeResponse(recipesRepository.addRecipe(request, userId))
        } catch (ex: IllegalArgumentException) {
            log.error(
                "400: Failed to create recipe for $request. Some parameter is missing or malformed!",
                ex
            )
        }
        return null
    }

    suspend fun deleteRecipe(recipeId: String, userId: UUID): Boolean {
        return recipesRepository.removeRecipe(recipeId, userId)
    }
}