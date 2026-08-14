package infrastructure.auth

import com.tenmilelabs.application.dto.*
import com.tenmilelabs.application.service.module
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import com.tenmilelabs.infrastructure.database.FakeRecipesRepository
import com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository
import com.tenmilelabs.infrastructure.database.FakeSyncRepository
import com.tenmilelabs.infrastructure.database.FakeUserPreferencesRepository
import com.tenmilelabs.infrastructure.database.FakeUserRepository
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals

class UserPreferencesIntegrationTest {

    @Test
    fun `GET user preferences returns 401 when unauthenticated`() = testApplication {
        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = FakeSyncRepository(),
                userPreferencesRepository = FakeUserPreferencesRepository()
            )
        }

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("/user/preferences") { accept(ContentType.Application.Json) }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET user preferences returns 204 when nothing saved yet`() = testApplication {
        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = FakeSyncRepository(),
                userPreferencesRepository = FakeUserPreferencesRepository()
            )
        }

        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()

        val response = client.get("/user/preferences") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `GET user preferences returns the preferences from the last pushed plan`() = testApplication {
        val syncRepository = FakeSyncRepository()
        val userPreferencesRepository = FakeUserPreferencesRepository()

        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = syncRepository,
                userPreferencesRepository = userPreferencesRepository
            )
        }

        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()
        val planId = UUID.randomUUID()

        client.post("/sync/push") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                SyncPushRequest(
                    recipes = emptyList(),
                    mealPlans = listOf(
                        SyncMealPlanDto(
                            uuid = planId.toString(),
                            name = "Week Plan",
                            status = "DRAFT",
                            preferencesJson = """{"planLengthDays":5,"mealType":"DINNER_AND_LUNCH","dietaryRestrictions":["VEGAN"],"recipeSource":"COLLECTION_ONLY","maxPrepTimeMinutes":30,"servingsPerMeal":4,"batchCooking":true,"leftoverFriendly":false,"varietyPreference":"MEDIUM"}""",
                            createdAt = 1000L,
                            updatedAt = 1000L,
                            deletedAt = null,
                            days = listOf(SyncMealPlanDayDto(uuid = UUID.randomUUID().toString(), dayIndex = 0, dinnerRecipeId = null, lunchRecipeId = null))
                        )
                    )
                )
            )
        }

        val response = client.get("/user/preferences") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<UserPreferencesResponse>()
        assertEquals(5, body.planLengthDays)
        assertEquals("DINNER_AND_LUNCH", body.mealType)
        assertEquals(listOf("VEGAN"), body.dietaryRestrictions)
        assertEquals("COLLECTION_ONLY", body.recipeSource)
        assertEquals(30, body.maxPrepTimeMinutes)
        assertEquals(4, body.servingsPerMeal)
        assertEquals(true, body.batchCooking)
        assertEquals(false, body.leftoverFriendly)
        assertEquals("MEDIUM", body.varietyPreference)
    }

    private suspend fun HttpClient.registerAndGetAuth(): AuthResponse {
        val registerRequest = RegisterRequest(
            email = "userprefs-test-${UUID.randomUUID()}@example.com",
            username = "userprefsuser-${UUID.randomUUID().toString().take(8)}",
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
