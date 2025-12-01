package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.domain.model.Privacy
import com.tenmilelabs.domain.model.Recipe
import com.tenmilelabs.domain.repository.RecipesRepository
import com.tenmilelabs.infrastructure.database.FakeUserRepository.Companion.TEST_USER_ID
import java.util.*

class FakeRecipesRepository(testUserId: UUID = TEST_USER_ID) : RecipesRepository {

    private val recipes = mutableListOf<Recipe>()

    init {
        recipes.addAll(
            listOf(
                Recipe(
                    uuid = "1",
                    title = "Recipe 1",
                    description = "Test recipe",
                    imageUrl = "http://example.com/image.jpg",
                    imageUrlThumbnail = "http://example.com/thumb.jpg",
                    prepTimeMinutes = 30,
                    cookTimeMinutes = 15,
                    servings = 2,
                    creatorId = testUserId.toString(),
                    recipeExternalUrl = "http://recipe.url",
                    privacy = Privacy.PUBLIC,
                    updatedAt = 123456789,
                    deletedAt = null,
                    syncState = "created",
                    serverUpdatedAt = "2023-01-01T00:00:00Z"
                ),
                Recipe(
                    uuid = "2",
                    title = "Recipe 2",
                    description = "Another test recipe",
                    imageUrl = "http://example.com/image2.jpg",
                    imageUrlThumbnail = "http://example.com/thumb2.jpg",
                    prepTimeMinutes = 40,
                    cookTimeMinutes = 20,
                    servings = 4,
                    creatorId = testUserId.toString(),
                    recipeExternalUrl = "http://recipe.url2",
                    privacy = Privacy.PRIVATE,
                    updatedAt = 123456789,
                    deletedAt = null,
                    syncState = "created",
                    serverUpdatedAt = "2023-01-01T00:00:00Z"
                )
            )
        )
    }

    override suspend fun allRecipes(): List<Recipe> = recipes

    override suspend fun recipesByUserId(userId: UUID): List<Recipe> =
        recipes.filter { it.creatorId == userId.toString() }

    override suspend fun publicRecipes(): List<Recipe> =
        recipes.filter { it.privacy == Privacy.PUBLIC }

    override suspend fun recipeByTitle(title: String): Recipe? =
        recipes.find { it.title == title }

    override suspend fun recipeById(id: String): Recipe? =
        recipes.find { it.uuid == id }

    override suspend fun recipeByIdAndUserId(id: String, userId: UUID): Recipe? =
        recipes.find { it.uuid == id && it.creatorId == userId.toString() }

    override suspend fun addRecipe(recipeRequest: CreateRecipeRequest, userId: UUID): String {
        if (recipeByTitle(recipeRequest.title) != null) {
            throw IllegalStateException("Cannot duplicate recipe titles!")
        }
        val nextId = (recipes.size + 1).toString()
        val recipe = Recipe(
            uuid = nextId,
            title = recipeRequest.title,
            description = recipeRequest.description,
            imageUrl = recipeRequest.imageUrl,
            imageUrlThumbnail = recipeRequest.imageUrlThumbnail,
            prepTimeMinutes = recipeRequest.prepTimeMinutes,
            cookTimeMinutes = recipeRequest.cookTimeMinutes,
            servings = recipeRequest.servings,
            creatorId = userId.toString(),
            recipeExternalUrl = recipeRequest.recipeExternalUrl,
            privacy = enumValueOf(recipeRequest.privacy),
            updatedAt = System.currentTimeMillis(),
            deletedAt = null,
            syncState = "created",
            serverUpdatedAt = "2023-01-01T00:00:00Z"
        )
        recipes.add(recipe)
        return nextId
    }

    override suspend fun removeRecipe(uuid: String, userId: UUID): Boolean =
        recipes.removeIf { it.uuid == uuid && it.creatorId == userId.toString() }
}