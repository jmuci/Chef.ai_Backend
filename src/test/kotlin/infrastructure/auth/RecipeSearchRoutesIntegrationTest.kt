package infrastructure.auth

import com.tenmilelabs.application.dto.AuthResponse
import com.tenmilelabs.application.dto.RecipeSearchResponse
import com.tenmilelabs.application.dto.RegisterRequest
import com.tenmilelabs.application.service.module
import com.tenmilelabs.domain.repository.RecipeSearchPage
import com.tenmilelabs.domain.repository.RecipeSearchRow
import com.tenmilelabs.infrastructure.database.FakeRecipeSearchRepository
import com.tenmilelabs.infrastructure.database.FakeRecipesRepository
import com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository
import com.tenmilelabs.infrastructure.database.FakeSyncRepository
import com.tenmilelabs.infrastructure.database.FakeUserRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecipeSearchRoutesIntegrationTest {

    @Test
    fun `search requires authentication`() = testApplication {
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = FakeSyncRepository(), recipeSearchRepository = FakeRecipeSearchRepository()) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.get("/api/v1/recipes/search?q=chicken")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `missing q is a 400`() = testApplication {
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = FakeSyncRepository(), recipeSearchRepository = FakeRecipeSearchRepository()) }
        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()

        val response = client.get("/api/v1/recipes/search") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `blank q is a 400`() = testApplication {
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = FakeSyncRepository(), recipeSearchRepository = FakeRecipeSearchRepository()) }
        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()

        val response = client.get("/api/v1/recipes/search?q=%20%20") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `an empty result set serializes results as an explicit empty array, not an omitted field`() = testApplication {
        val fakeRepository = FakeRecipeSearchRepository()
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = FakeSyncRepository(), recipeSearchRepository = fakeRepository) }
        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()

        val response = client.get("/api/v1/recipes/search?q=nothingmatches") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        // The response DTO has no default values specifically so a client-side deserialization bug
        // reading a missing field fails loudly instead of silently seeing its Kotlin default — this
        // asserts the field is actually present in the raw JSON, not merely equal after decoding.
        assertTrue(response.bodyAsText().contains("\"results\":[]"))
    }

    @Test
    fun `limit and offset are clamped before reaching the repository`() = testApplication {
        val fakeRepository = FakeRecipeSearchRepository()
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = FakeSyncRepository(), recipeSearchRepository = fakeRepository) }
        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()

        val response = client.get("/api/v1/recipes/search?q=chicken&limit=9999&offset=-5") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        // Requested limit 9999 -> clamped to MAX_LIMIT (50); offset -5 -> clamped to 0.
        assertEquals(50, fakeRepository.lastLimit)
        assertEquals(0, fakeRepository.lastOffset)
    }

    @Test
    fun `hasMore from the repository page is surfaced in the response`() = testApplication {
        val recipeId = UUID.randomUUID()
        val fakeRepository = FakeRecipeSearchRepository().apply {
            pageToReturn = RecipeSearchPage(
                rows = listOf(
                    RecipeSearchRow(
                        uuid = recipeId,
                        title = "Chicken Soup",
                        description = "Warm and comforting",
                        imageUrl = "https://example.com/img.jpg",
                        imageUrlThumbnail = "https://example.com/thumb.jpg",
                        prepTimeMinutes = 10,
                        cookTimeMinutes = 20,
                        servings = 4,
                        creatorId = UUID.randomUUID(),
                        privacy = "PUBLIC",
                        updatedAt = 1_000L,
                    )
                ),
                hasMore = true,
            )
        }
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = FakeSyncRepository(), recipeSearchRepository = fakeRepository) }
        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()

        val response = client.get("/api/v1/recipes/search?q=chicken") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<RecipeSearchResponse>()
        assertTrue(body.hasMore)
        assertEquals(1, body.results.size)
        assertEquals(recipeId.toString(), body.results.first().uuid)
    }

    private suspend fun HttpClient.registerAndGetAuth(): AuthResponse {
        val registerRequest = RegisterRequest(
            email = "recipe-search-test@example.com",
            username = "recipesearchuser",
            password = "TestPassword123!"
        )

        val registerResponse = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        assertEquals(HttpStatusCode.Created, registerResponse.status)
        return registerResponse.body()
    }
}
