package infrastructure.auth

import com.tenmilelabs.application.dto.AuthResponse
import com.tenmilelabs.application.dto.RecipeDetailResponse
import com.tenmilelabs.application.dto.RegisterRequest
import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.application.dto.SyncRecipeIngredient
import com.tenmilelabs.application.dto.SyncRecipeStep
import com.tenmilelabs.application.service.module
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
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecipeDetailRoutesIntegrationTest {

    @Test
    fun `an anonymous request for a PUBLIC recipe is served`() = testApplication {
        val recipeId = UUID.randomUUID()
        val fakeRepository = FakeSyncRepository().apply {
            seedRecipe(sampleRecipe(recipeId, UUID.randomUUID(), privacy = "PUBLIC"), serverUpdatedAtMillis = 1000L)
        }
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = fakeRepository, recipeSearchRepository = FakeRecipeSearchRepository()) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.get("/api/v1/recipes/$recipeId") {
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(recipeId.toString(), response.body<RecipeDetailResponse>().recipe.uuid)
    }

    @Test
    fun `an anonymous request for a PRIVATE recipe is a 404`() = testApplication {
        val recipeId = UUID.randomUUID()
        val fakeRepository = FakeSyncRepository().apply {
            seedRecipe(sampleRecipe(recipeId, UUID.randomUUID(), privacy = "PRIVATE"), serverUpdatedAtMillis = 1000L)
        }
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = fakeRepository, recipeSearchRepository = FakeRecipeSearchRepository()) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.get("/api/v1/recipes/$recipeId") {
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `the owner can fetch their own PRIVATE recipe`() = testApplication {
        val fakeRepository = FakeSyncRepository()
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = fakeRepository, recipeSearchRepository = FakeRecipeSearchRepository()) }
        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()

        val recipeId = UUID.randomUUID()
        fakeRepository.seedRecipe(
            sampleRecipe(recipeId, UUID.fromString(auth.userId), privacy = "PRIVATE"),
            serverUpdatedAtMillis = 1000L
        )

        val response = client.get("/api/v1/recipes/$recipeId") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `a different authenticated user cannot fetch someone else's PRIVATE recipe`() = testApplication {
        val fakeRepository = FakeSyncRepository()
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = fakeRepository, recipeSearchRepository = FakeRecipeSearchRepository()) }
        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()

        val recipeId = UUID.randomUUID()
        fakeRepository.seedRecipe(
            sampleRecipe(recipeId, UUID.randomUUID(), privacy = "PRIVATE"),
            serverUpdatedAtMillis = 1000L
        )

        val response = client.get("/api/v1/recipes/$recipeId") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `a present but invalid bearer token is still rejected instead of silently downgraded`() = testApplication {
        val recipeId = UUID.randomUUID()
        val fakeRepository = FakeSyncRepository().apply {
            seedRecipe(sampleRecipe(recipeId, UUID.randomUUID(), privacy = "PUBLIC"), serverUpdatedAtMillis = 1000L)
        }
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = fakeRepository, recipeSearchRepository = FakeRecipeSearchRepository()) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.get("/api/v1/recipes/$recipeId") {
            bearerAuth("not-a-real-jwt")
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `a malformed recipeId is a 400`() = testApplication {
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = FakeSyncRepository(), recipeSearchRepository = FakeRecipeSearchRepository()) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.get("/api/v1/recipes/not-a-uuid") {
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `a well-formed but nonexistent recipeId is a 404`() = testApplication {
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = FakeSyncRepository(), recipeSearchRepository = FakeRecipeSearchRepository()) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.get("/api/v1/recipes/${UUID.randomUUID()}") {
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `a soft-deleted PUBLIC recipe is a 404`() = testApplication {
        val recipeId = UUID.randomUUID()
        val fakeRepository = FakeSyncRepository().apply {
            seedRecipe(
                sampleRecipe(recipeId, UUID.randomUUID(), privacy = "PUBLIC").copy(deletedAt = 1500L),
                serverUpdatedAtMillis = 1500L
            )
        }
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = fakeRepository, recipeSearchRepository = FakeRecipeSearchRepository()) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.get("/api/v1/recipes/$recipeId") {
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `the response carries reference data and the creator for the recipe's own dependencies`() = testApplication {
        val recipeId = UUID.randomUUID()
        val creatorId = UUID.randomUUID()
        val fakeRepository = FakeSyncRepository()
        val ingredientId = fakeRepository.seedIngredient()
        val tagId = fakeRepository.seedTag()
        // seedRecipe auto-seeds the creator into `users` if not already present.
        fakeRepository.seedRecipe(
            sampleRecipe(recipeId, creatorId, privacy = "PUBLIC", ingredientId = ingredientId, tagId = tagId),
            serverUpdatedAtMillis = 1000L
        )
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = fakeRepository, recipeSearchRepository = FakeRecipeSearchRepository()) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.get("/api/v1/recipes/$recipeId") {
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<RecipeDetailResponse>()
        assertTrue(body.referenceData.ingredients.any { it.uuid == ingredientId.toString() })
        assertTrue(body.referenceData.tags.any { it.uuid == tagId.toString() })
        assertTrue(body.creators.any { it.uuid == creatorId.toString() })
    }

    @Test
    fun `GET recipes search still resolves correctly with the recipeId route mounted alongside it`() = testApplication {
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = FakeSyncRepository(), recipeSearchRepository = FakeRecipeSearchRepository()) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.get("/api/v1/recipes/search?q=chicken") {
            accept(ContentType.Application.Json)
        }

        // A shadowed request would hit recipeDetailRoutes instead and 400 on "search is not a
        // valid UUID" - 200 here pins that Ktor's literal-segment-over-parameter routing
        // priority (see RecipeDetailRoutes.kt/the implementation plan) actually holds.
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private fun sampleRecipe(
        uuid: UUID,
        creatorId: UUID,
        privacy: String,
        ingredientId: UUID = UUID.randomUUID(),
        tagId: UUID? = null,
    ): SyncRecipe = SyncRecipe(
        uuid = uuid.toString(),
        title = "Recipe",
        description = "Description",
        imageUrl = "https://example.com/image.jpg",
        imageUrlThumbnail = "https://example.com/thumb.jpg",
        prepTimeMinutes = 10,
        cookTimeMinutes = 20,
        servings = 2,
        creatorId = creatorId.toString(),
        recipeExternalUrl = null,
        privacy = privacy,
        updatedAt = 1000L,
        deletedAt = null,
        steps = listOf(SyncRecipeStep(UUID.randomUUID().toString(), 0, "Step")),
        ingredients = listOf(SyncRecipeIngredient(ingredientId.toString(), 1.0, "unit")),
        tagIds = listOfNotNull(tagId?.toString()),
        labelIds = emptyList()
    )

    private suspend fun HttpClient.registerAndGetAuth(): AuthResponse {
        val registerRequest = RegisterRequest(
            email = "recipe-detail-test@example.com",
            username = "recipedetailuser",
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
