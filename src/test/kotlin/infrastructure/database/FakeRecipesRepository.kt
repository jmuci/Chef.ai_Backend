package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.domain.model.Label
import com.tenmilelabs.domain.model.Recipe
import com.tenmilelabs.domain.repository.RecipesRepository
import com.tenmilelabs.infrastructure.database.FakeUserRepository.Companion.TEST_USER_ID
import java.util.*

class FakeRecipesRepository(testUserId: UUID = TEST_USER_ID) : RecipesRepository {

    val testRecipe1 = Recipe(
        uuid = "1",
        title = "Recipe 1",
        description = "Recipe Description 1",
        label = Label.Mediterranean,
        recipeUrl = "http://example.com/recipe",
        imageUrl = "http://example.com/image.jpg",
        imageUrlThumbnail = "http://example.com/thumb.jpg",
        prepTimeMins = 30,
        userId = testUserId.toString(),
        isPublic = false
    )
    val testRecipe2 = Recipe(
        uuid = "2",
        title = "Recipe 2",
        description = "Recipe Description 2",
        label = Label.Mediterranean,
        recipeUrl = "http://example.com/recipe",
        imageUrl = "http://example.com/image.jpg",
        imageUrlThumbnail = "http://example.com/thumb.jpg",
        prepTimeMins = 40,
        userId = testUserId.toString(),
        isPublic = true
    )
    val testRecipe3 = Recipe(
        uuid = "3",
        title = "Recipe 3",
        description = "Recipe Description 3",
        label = Label.Vegetarian,
        recipeUrl = "http://example.com/recipe",
        imageUrl = "http://example.com/image.jpg",
        imageUrlThumbnail = "http://example.com/thumb.jpg",
        prepTimeMins = 50,
        userId = "user2",
        isPublic = true
    )
    val testRecipe4 = Recipe(
        uuid = "4",
        title = "Recipe 4",
        description = "Recipe Description 4",
        label = Label.LowCarb,
        recipeUrl = "http://example.com/recipe",
        imageUrl = "http://example.com/image.jpg",
        imageUrlThumbnail = "http://example.com/thumb.jpg",
        prepTimeMins = 60,
        userId = "user2",
        isPublic = false
    )
    val testRecipe5 = Recipe(
        uuid = "5",
        title = "Recipe 5",
        description = "Recipe Description 5",
        label = Label.LowCarb,
        recipeUrl = "http://example.com/recipe",
        imageUrl = "http://example.com/image.jpg",
        imageUrlThumbnail = "http://example.com/thumb.jpg",
        prepTimeMins = 60,
        userId = testUserId.toString(),
        isPublic = false
    )
    val testRecipe6 = Recipe(
        uuid = "6",
        title = "Recipe 6",
        description = "Recipe Description 6",
        label = Label.Vegetarian,
        recipeUrl = "http://example.com/recipe",
        imageUrl = "http://example.com/image.jpg",
        imageUrlThumbnail = "http://example.com/thumb.jpg",
        prepTimeMins = 60,
        userId = testUserId.toString(),
        isPublic = true
    )
    val testRecipeToBeDeleted1 = Recipe(
        uuid = "recipeToBeDeleted1",
        title = "Recipe To Be DELETED 1",
        description = "Recipe Description 6",
        label = Label.French,
        recipeUrl = "http://example.com/recipe",
        imageUrl = "http://example.com/image.jpg",
        imageUrlThumbnail = "http://example.com/thumb.jpg",
        prepTimeMins = 70,
        userId = testUserId.toString(),
        isPublic = false
    )

    private val recipes = mutableListOf(testRecipe1, testRecipe2, testRecipe3, testRecipe4, testRecipe5, testRecipe6, testRecipeToBeDeleted1)

    override suspend fun allRecipes(): List<Recipe> = recipes

    override suspend fun recipesByUserId(userId: UUID): List<Recipe> =
        recipes.filter { it.userId == userId.toString() }

    override suspend fun publicRecipes(): List<Recipe> =
        recipes.filter { it.isPublic }

    override suspend fun recipesByLabel(label: Label): List<Recipe> = recipes.filter { it.label == label }

    override suspend fun recipeByTitle(title: String) = recipes.find { it.title.equals(title, ignoreCase = true) }

    override suspend fun recipeById(id: String) = recipes.find { it.uuid == id }

    override suspend fun recipeByIdAndUserId(id: String, userId: UUID): Recipe? =
        recipes.find { it.uuid == id && it.userId == userId.toString() }

    override suspend fun addRecipe(recipeRequest: CreateRecipeRequest, userId: UUID): String {
        if (recipeByTitle(recipeRequest.title) != null) {
            throw IllegalStateException("Cannot duplicate recipe titles!")
        }
        val recipe = Recipe(
            uuid = UUID.randomUUID().toString(),
            title = recipeRequest.title,
            description = recipeRequest.description,
            label = Label.valueOf(recipeRequest.label),
            recipeUrl = recipeRequest.recipeUrl,
            imageUrl = recipeRequest.imageUrl,
            imageUrlThumbnail = recipeRequest.imageUrlThumbnail,
            prepTimeMins = Integer.valueOf(recipeRequest.prepTimeMins),
            userId = userId.toString(),
            isPublic = false
        )
        recipes.add(recipe)
        return recipe.uuid
    }

    override suspend fun removeRecipe(uuid: String, userId: UUID): Boolean =
        recipes.removeIf { it.uuid == uuid && it.userId == userId.toString() }
}