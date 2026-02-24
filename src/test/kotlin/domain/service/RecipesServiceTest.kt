package com.tenmilelabs.domain.service

import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.domain.model.Privacy
import com.tenmilelabs.infrastructure.database.FakeRecipesRepository
import com.tenmilelabs.infrastructure.database.FakeUserRepository
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class RecipesServiceTest {
    private lateinit var recipesRepository: FakeRecipesRepository
    private lateinit var recipesService: RecipesService
    private val logger = KtorSimpleLogger("RecipesServiceTest")
    private val testUserId = FakeUserRepository.TEST_USER_ID

    @BeforeEach
    fun setup() {
        recipesRepository = FakeRecipesRepository()
        recipesService = RecipesService(recipesRepository, logger)
    }

    @Test
    fun `getAllRecipes returns all recipes`() = runTest {
        val recipes = recipesService.getAllRecipes()
        assertEquals(3, recipes.size)
    }

    @Test
    fun `getRecipesByUserId returns only user recipes`() = runTest {
        val recipes = recipesService.getRecipesByUserId(testUserId)
        assertEquals(2, recipes.size)
        assertTrue(recipes.all { it.creatorId == testUserId.toString() })
    }

    @Test
    fun `getPublicRecipes returns only public recipes`() = runTest {
        val recipes = recipesService.getPublicRecipes()
        assertEquals(1, recipes.size)
        assertTrue(recipes.all { it.privacy == Privacy.PUBLIC })
    }

    @Test
    fun `getAccessibleRecipes returns own plus public distinct`() = runTest {
        val recipes = recipesService.getAccessibleRecipes(testUserId)
        assertEquals(2, recipes.size)
        assertTrue(recipes.any { it.uuid == "1" })
        assertTrue(recipes.any { it.uuid == "2" })
    }

    @Test
    fun `getRecipeById returns owned private recipe`() = runTest {
        val recipe = recipesService.getRecipeById("2", testUserId)
        assertNotNull(recipe)
        assertEquals("2", recipe.uuid)
    }

    @Test
    fun `getRecipeById returns public recipe even when not owner`() = runTest {
        val otherUserId = UUID.randomUUID()
        val recipe = recipesService.getRecipeById("1", otherUserId)
        assertNotNull(recipe)
        assertEquals(Privacy.PUBLIC, recipe.privacy)
    }

    @Test
    fun `getRecipeById returns null for private recipe of another user`() = runTest {
        val recipe = recipesService.getRecipeById("3", testUserId)
        assertNull(recipe)
    }

    @Test
    fun `createRecipe returns response for valid input`() = runTest {
        val request = CreateRecipeRequest(
            title = "New Recipe",
            description = "Test",
            imageUrl = "https://example.com/image.jpg",
            imageUrlThumbnail = "https://example.com/thumb.jpg",
            prepTimeMinutes = 10,
            cookTimeMinutes = 15,
            servings = 2,
            recipeExternalUrl = null,
            privacy = "PUBLIC"
        )

        val response = recipesService.createRecipe(request, testUserId)
        assertNotNull(response)
    }

    @Test
    fun `createRecipe returns null for invalid privacy`() = runTest {
        val request = CreateRecipeRequest(
            title = "Invalid Recipe",
            description = "Test",
            imageUrl = "https://example.com/image.jpg",
            imageUrlThumbnail = "https://example.com/thumb.jpg",
            prepTimeMinutes = 10,
            cookTimeMinutes = 15,
            servings = 2,
            recipeExternalUrl = null,
            privacy = "INVALID"
        )

        val response = recipesService.createRecipe(request, testUserId)
        assertNull(response)
    }

    @Test
    fun `deleteRecipe returns true when user owns recipe`() = runTest {
        val deleted = recipesService.deleteRecipe("1", testUserId)
        assertTrue(deleted)
    }

    @Test
    fun `deleteRecipe returns false when user does not own recipe`() = runTest {
        val deleted = recipesService.deleteRecipe("3", testUserId)
        assertFalse(deleted)
    }
}
