package infrastructure.auth

import com.tenmilelabs.application.dto.AuthResponse
import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.application.dto.GenerateMealPlanStatelessRequest
import com.tenmilelabs.application.dto.GenerateMealPlanStatelessResponse
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
import io.ktor.client.request.bearerAuth
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

class MealPlanGenerationRoutesIntegrationTest {

    @Test
    fun `an anonymous request with no Authorization header is served, not challenged`() = testApplication {
        val fakeRepository = FakeSyncRepository()
        seedCandidateRecipe(fakeRepository)
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = fakeRepository, recipeSearchRepository = FakeRecipeSearchRepository()) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.post("/api/v1/meal-plans/generate") {
            contentType(ContentType.Application.Json)
            setBody(GenerateMealPlanStatelessRequest("""{"planLengthDays":3,"recipeSource":"INCLUDE_PUBLIC"}"""))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(3, response.body<GenerateMealPlanStatelessResponse>().days.size)
    }

    @Test
    fun `a request with a valid JWT is served`() = testApplication {
        val fakeRepository = FakeSyncRepository()
        seedCandidateRecipe(fakeRepository)
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = fakeRepository, recipeSearchRepository = FakeRecipeSearchRepository()) }
        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()

        val response = client.post("/api/v1/meal-plans/generate") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(GenerateMealPlanStatelessRequest("""{"planLengthDays":3,"recipeSource":"INCLUDE_PUBLIC"}"""))
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `malformed preferencesJson is a 400, not a 500`() = testApplication {
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = FakeSyncRepository(), recipeSearchRepository = FakeRecipeSearchRepository()) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.post("/api/v1/meal-plans/generate") {
            contentType(ContentType.Application.Json)
            setBody(GenerateMealPlanStatelessRequest("not valid json"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        response.body<ErrorResponse>()
    }

    @Test
    fun `a blank preferencesJson is a 400`() = testApplication {
        application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = FakeSyncRepository(), recipeSearchRepository = FakeRecipeSearchRepository()) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.post("/api/v1/meal-plans/generate") {
            contentType(ContentType.Application.Json)
            setBody(GenerateMealPlanStatelessRequest("   "))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `every returned dinnerRecipeId and lunchRecipeId appears in the response's own recipes list`() =
        testApplication {
            val fakeRepository = FakeSyncRepository()
            repeat(5) { seedCandidateRecipe(fakeRepository) }
            application { module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = FakeRefreshTokenRepository(), syncRepository = fakeRepository, recipeSearchRepository = FakeRecipeSearchRepository()) }
            val client = createClient { install(ContentNegotiation) { json() } }

            val response = client.post("/api/v1/meal-plans/generate") {
                contentType(ContentType.Application.Json)
                setBody(
                    GenerateMealPlanStatelessRequest(
                        """{"planLengthDays":5,"mealType":"DINNER_AND_LUNCH","recipeSource":"INCLUDE_PUBLIC"}"""
                    )
                )
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<GenerateMealPlanStatelessResponse>()
            val referencedIds = body.days.flatMap { listOfNotNull(it.dinnerRecipeId, it.lunchRecipeId) }.toSet()
            val returnedRecipeIds = body.recipes.map { it.uuid }.toSet()
            assertTrue(referencedIds.isNotEmpty())
            assertTrue(referencedIds.all { it in returnedRecipeIds })
        }

    private fun seedCandidateRecipe(repo: FakeSyncRepository, uuid: UUID = UUID.randomUUID()): UUID {
        repo.seedRecipe(sampleRecipe(uuid, UUID.randomUUID(), privacy = "PUBLIC"), serverUpdatedAtMillis = 1000L)
        repo.seedCandidateRecipe(uuid)
        return uuid
    }

    private fun sampleRecipe(
        uuid: UUID,
        creatorId: UUID,
        privacy: String,
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
        ingredients = listOf(SyncRecipeIngredient(UUID.randomUUID().toString(), 1.0, "unit")),
        tagIds = emptyList(),
        labelIds = emptyList()
    )

    private suspend fun HttpClient.registerAndGetAuth(): AuthResponse {
        val registerRequest = RegisterRequest(
            email = "meal-plan-generation-test@example.com",
            username = "mealplangenuser",
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
