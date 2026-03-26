package presentation.routes

import com.tenmilelabs.application.dto.AuthResponse
import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.application.dto.ExtractRecipeRequest
import com.tenmilelabs.application.dto.RecipeExtractionResponse
import com.tenmilelabs.application.dto.RegisterRequest
import com.tenmilelabs.application.service.module
import com.tenmilelabs.domain.exception.FetchFailedException
import com.tenmilelabs.domain.exception.NoRecipeDataException
import com.tenmilelabs.domain.model.ExtractedRecipe
import com.tenmilelabs.domain.model.ParsedIngredient
import com.tenmilelabs.domain.model.RecipeStep
import com.tenmilelabs.infrastructure.database.FakeRecipesRepository
import com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository
import com.tenmilelabs.infrastructure.database.FakeUserRepository
import infrastructure.extraction.FakeRecipeExtractor
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtractionRoutesIntegrationTest {

    private val sampleRecipe = ExtractedRecipe(
        title = "Test Chocolate Cake",
        description = "Rich and moist",
        servings = "8 servings",
        prepTimeMinutes = 30,
        cookTimeMinutes = 60,
        totalTimeMinutes = 90,
        ingredients = listOf(
            ParsedIngredient(raw = "2 cups flour", quantity = "2", unit = "cup", name = "flour", preparation = null),
            ParsedIngredient(raw = "1 cup sugar", quantity = "1", unit = "cup", name = "sugar", preparation = null)
        ),
        steps = listOf(
            RecipeStep(order = 1, text = "Mix dry ingredients"),
            RecipeStep(order = 2, text = "Bake at 350F")
        ),
        tags = listOf("dessert", "chocolate"),
        yieldAmount = "8 servings",
        sourceUrl = "https://example.com/recipe"
    )

    private fun ApplicationTestBuilder.configureApp(fakeExtractor: FakeRecipeExtractor) {
        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                recipeExtractor = fakeExtractor
            )
        }
    }

    private fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
        install(ContentNegotiation) { json() }
    }

    private suspend fun HttpClient.registerAndGetAuth(): AuthResponse {
        val response = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(
                email = "extract-test@example.com",
                username = "extractuser",
                password = "TestPassword123!"
            ))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    @Test
    fun happyPathReturns200WithRecipeData() = testApplication {
        val fakeExtractor = FakeRecipeExtractor().apply { result = sampleRecipe }
        configureApp(fakeExtractor)

        val client = jsonClient()
        val auth = client.registerAndGetAuth()

        val response = client.post("/api/recipes/extract") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(ExtractRecipeRequest(url = "https://example.com/recipe"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<RecipeExtractionResponse>()
        assertEquals("Test Chocolate Cake", body.title)
        assertEquals("Rich and moist", body.description)
        assertEquals(30, body.prepTimeMinutes)
        assertEquals(60, body.cookTimeMinutes)
        assertEquals(90, body.totalTimeMinutes)
        assertEquals(2, body.ingredients.size)
        assertEquals("flour", body.ingredients[0].name)
        assertEquals(2, body.steps.size)
        assertEquals("https://example.com/recipe", body.sourceUrl)
        assertTrue(body.tags.contains("dessert"))
    }

    @Test
    fun blankUrlReturns400() = testApplication {
        val fakeExtractor = FakeRecipeExtractor().apply { result = sampleRecipe }
        configureApp(fakeExtractor)

        val client = jsonClient()
        val auth = client.registerAndGetAuth()

        val response = client.post("/api/recipes/extract") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(ExtractRecipeRequest(url = ""))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun malformedUrlReturns400() = testApplication {
        val fakeExtractor = FakeRecipeExtractor().apply { result = sampleRecipe }
        configureApp(fakeExtractor)

        val client = jsonClient()
        val auth = client.registerAndGetAuth()

        val response = client.post("/api/recipes/extract") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(ExtractRecipeRequest(url = "ftp://not-http.com"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun fetchFailedReturns502() = testApplication {
        val fakeExtractor = FakeRecipeExtractor().apply {
            exception = FetchFailedException("https://down.com", "Connection refused")
        }
        configureApp(fakeExtractor)

        val client = jsonClient()
        val auth = client.registerAndGetAuth()

        val response = client.post("/api/recipes/extract") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(ExtractRecipeRequest(url = "https://down.com/recipe"))
        }

        assertEquals(HttpStatusCode.BadGateway, response.status)
        val body = response.body<ErrorResponse>()
        assertTrue(body.message.contains("Connection refused"))
    }

    @Test
    fun noRecipeDataReturns422() = testApplication {
        val fakeExtractor = FakeRecipeExtractor().apply {
            exception = NoRecipeDataException("https://example.com", "No structured recipe data")
        }
        configureApp(fakeExtractor)

        val client = jsonClient()
        val auth = client.registerAndGetAuth()

        val response = client.post("/api/recipes/extract") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(ExtractRecipeRequest(url = "https://example.com/no-recipe"))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun unauthenticatedReturns401() = testApplication {
        val fakeExtractor = FakeRecipeExtractor().apply { result = sampleRecipe }
        configureApp(fakeExtractor)

        val client = jsonClient()

        val response = client.post("/api/recipes/extract") {
            contentType(ContentType.Application.Json)
            setBody(ExtractRecipeRequest(url = "https://example.com/recipe"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
