package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.domain.model.Privacy
import com.tenmilelabs.domain.model.Recipe
import com.tenmilelabs.domain.repository.RecipesRepository
import com.tenmilelabs.infrastructure.database.FakeUserRepository.Companion.TEST_USER_ID
import kotlinx.datetime.Instant
import java.util.*

class FakeRecipesRepository(testUserId: UUID = TEST_USER_ID) : RecipesRepository {

    companion object {
        // Real UUIDs, not "1"/"2"/"3". PostgresRecipesRepository parses every id through
        // UUID.fromString, so a fake using sequential strings quietly accepted ids that
        // production rejects — which is how a 500 on a malformed /recipes/byId?uuid= went
        // unnoticed. Fixed values (not randomUUID) keep tests deterministic.
        val PUBLIC_RECIPE_ID: UUID = UUID.fromString("11111111-1111-4111-8111-111111111111")
        val OWNED_PRIVATE_RECIPE_ID: UUID = UUID.fromString("22222222-2222-4222-8222-222222222222")
        val FOREIGN_PRIVATE_RECIPE_ID: UUID = UUID.fromString("33333333-3333-4333-8333-333333333333")
    }

    private val recipes = mutableListOf<Recipe>()

    init {
        recipes.addAll(
            listOf(
                Recipe(
                    uuid = PUBLIC_RECIPE_ID.toString(),
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
                    serverUpdatedAt = "2023-01-01T00:00:00Z"
                ),
                Recipe(
                    uuid = OWNED_PRIVATE_RECIPE_ID.toString(),
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
                    serverUpdatedAt = "2023-01-01T00:00:00Z"
                ),
                Recipe(
                    uuid = FOREIGN_PRIVATE_RECIPE_ID.toString(),
                    title = "Recipe 3",
                    description = "Another test recipe",
                    imageUrl = "http://example.com/image3.jpg",
                    imageUrlThumbnail = "http://example.com/thumb3.jpg",
                    prepTimeMinutes = 40,
                    cookTimeMinutes = 20,
                    servings = 4,
                    creatorId = UUID.randomUUID().toString(),
                    recipeExternalUrl = "http://recipe.url2",
                    privacy = Privacy.PRIVATE,
                    updatedAt = 123456789,
                    deletedAt = null,
                    serverUpdatedAt = "2023-01-01T00:00:00Z"
                )
            )
        )
    }

    override suspend fun allRecipes(): List<Recipe> = recipes.filter { it.deletedAt == null }

    override suspend fun recipesByUserId(userId: UUID): List<Recipe> =
        recipes.filter { it.creatorId == userId.toString() && it.deletedAt == null }

    override suspend fun publicRecipes(): List<Recipe> =
        recipes.filter { it.privacy == Privacy.PUBLIC && it.deletedAt == null }

    override suspend fun recipeByTitle(title: String): Recipe? =
        recipes.find { it.title == title && it.deletedAt == null }

    override suspend fun recipeById(id: String): Recipe? =
        recipes.find { it.uuid == id && it.deletedAt == null }

    override suspend fun recipeByIdAndUserId(id: String, userId: UUID): Recipe? =
        recipes.find { it.uuid == id && it.creatorId == userId.toString() && it.deletedAt == null }

    override suspend fun addRecipe(recipeRequest: CreateRecipeRequest, userId: UUID): String {
        if (recipeByTitle(recipeRequest.title) != null) {
            throw IllegalStateException("Cannot duplicate recipe titles!")
        }
        val nextId = UUID.randomUUID().toString()
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
            serverUpdatedAt = "2023-01-01T00:00:00Z"
        )
        recipes.add(recipe)
        return nextId
    }

    override suspend fun removeRecipe(uuid: String, userId: UUID): Boolean {
        val recipeIndex = recipes.indexOfFirst {
            it.uuid == uuid && it.creatorId == userId.toString() && it.deletedAt == null
        }
        if (recipeIndex == -1) return false

        recipes[recipeIndex] = recipes[recipeIndex].copy(deletedAt = System.currentTimeMillis())
        return true
    }

    override suspend fun purgeSoftDeletedRecipes(olderThanMillis: Long, limit: Int): Int {
        if (limit <= 0) return 0

        val candidates = recipes
            .asSequence()
            // Mirrors DefaultRecipeRepository: retention runs off the server-stamped timestamp,
            // never the client-supplied deletedAt.
            .filter { it.deletedAt != null && Instant.parse(it.serverUpdatedAt).toEpochMilliseconds() <= olderThanMillis }
            .sortedBy { it.serverUpdatedAt }
            .take(limit)
            .map { it.uuid }
            .toSet()

        val before = recipes.size
        recipes.removeIf { it.uuid in candidates }
        return before - recipes.size
    }
}
