package infrastructure.auth

import com.tenmilelabs.application.dto.*
import com.tenmilelabs.application.service.module
import com.tenmilelabs.domain.service.MealPlanGenerationService
import com.tenmilelabs.infrastructure.database.FakeRecipesRepository
import com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository
import com.tenmilelabs.infrastructure.database.FakeSyncRepository
import com.tenmilelabs.infrastructure.database.FakeUserPreferencesRepository
import com.tenmilelabs.infrastructure.database.FakeUserRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import io.ktor.util.logging.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MealPlanIntegrationTest {

    @Test
    fun `push DRAFT plan is persisted and returned on pull`() = testApplication {
        val syncRepository = FakeSyncRepository()

        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = syncRepository,
                userPreferencesRepository = FakeUserPreferencesRepository()
            )
        }

        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()
        val planId = UUID.randomUUID()

        // Push a DRAFT meal plan
        val pushResponse = client.post("/sync/push") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                SyncPushRequest(
                    recipes = emptyList(),
                    mealPlans = listOf(buildDraftPlan(planId, updatedAt = 1000L))
                )
            )
        }

        assertEquals(HttpStatusCode.OK, pushResponse.status)
        val pushBody = pushResponse.body<SyncPushResponse>()
        assertEquals(1, pushBody.mealPlans.accepted.size)
        assertEquals(planId.toString(), pushBody.mealPlans.accepted.first().uuid)
        assertTrue(pushBody.mealPlans.conflicts.isEmpty())
        assertTrue(pushBody.mealPlans.errors.isEmpty())

        // Pull and verify the plan is returned
        val pullResponse = client.get("/sync/pull?since=0&limit=100") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, pullResponse.status)
        val pullBody = pullResponse.body<SyncPullResponse>()
        val returnedPlan = pullBody.mealPlans.firstOrNull { it.uuid == planId.toString() }
        assertNotNull(returnedPlan)
        assertEquals("DRAFT", returnedPlan.status)
        assertEquals("Week Plan", returnedPlan.name)
    }

    @Test
    fun `push with newer server timestamp returns conflict`() = testApplication {
        val syncRepository = FakeSyncRepository()
        val planId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = syncRepository,
                userPreferencesRepository = FakeUserPreferencesRepository()
            )
        }

        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()

        // Seed an existing plan with a high server timestamp, owned by the same user who is
        // about to push a stale update - getMealPlanForUser only sees plans owned by the caller.
        syncRepository.seedMealPlan(
            buildDraftPlan(planId, updatedAt = 9000L),
            serverUpdatedAtMillis = 9000L,
            userId = UUID.fromString(auth.userId)
        )

        // Push a stale version (client updatedAt < server serverUpdatedAt)
        val pushResponse = client.post("/sync/push") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                SyncPushRequest(
                    recipes = emptyList(),
                    mealPlans = listOf(buildDraftPlan(planId, updatedAt = 1000L))
                )
            )
        }

        assertEquals(HttpStatusCode.OK, pushResponse.status)
        val body = pushResponse.body<SyncPushResponse>()
        assertTrue(body.mealPlans.accepted.isEmpty())
        assertEquals(1, body.mealPlans.conflicts.size)
        assertEquals(planId.toString(), body.mealPlans.conflicts.first())
    }

    @Test
    fun `generate endpoint returns 404 for unknown plan`() = testApplication {
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

        val response = client.post("/meal-plans/${UUID.randomUUID()}/generate") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `generate endpoint returns 400 for a malformed mealPlanId`() = testApplication {
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

        val response = client.post("/meal-plans/not-a-uuid/generate") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `generate endpoint returns 404 for a plan owned by a different user`() = testApplication {
        // startGeneration folds "doesn't exist" and "exists but isn't owned by the caller" into
        // the same 404 - this pins that the authorization branch is taken, not just the existence
        // branch above, so a query that dropped its ownership filter wouldn't slip through.
        val syncRepository = FakeSyncRepository()
        val planId = UUID.randomUUID()

        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = syncRepository,
                userPreferencesRepository = FakeUserPreferencesRepository()
            )
        }

        val client = createClient { install(ContentNegotiation) { json() } }
        val owner = client.registerAndGetAuth()
        val intruder = client.registerAndGetAuth()

        client.post("/sync/push") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(SyncPushRequest(recipes = emptyList(), mealPlans = listOf(buildDraftPlan(planId))))
        }

        val response = client.post("/meal-plans/$planId/generate") {
            bearerAuth(intruder.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `generate endpoint returns 202 and sets plan to GENERATING`() = testApplication {
        val syncRepository = FakeSyncRepository()
        val planId = UUID.randomUUID()

        val testDispatcher = StandardTestDispatcher()
        val testScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val log = KtorSimpleLogger("test")
        val generationService = MealPlanGenerationService(syncRepository, testScope, log)

        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = syncRepository,
                mealPlanGenerationService = generationService,
                userPreferencesRepository = FakeUserPreferencesRepository()
            )
        }

        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()

        // Push a DRAFT plan first
        client.post("/sync/push") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(SyncPushRequest(recipes = emptyList(), mealPlans = listOf(buildDraftPlan(planId))))
        }

        // Trigger generation
        val generateResponse = client.post("/meal-plans/$planId/generate") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Accepted, generateResponse.status)
        val body = generateResponse.body<GenerateMealPlanResponse>()
        assertEquals(planId.toString(), body.uuid)
        assertEquals("GENERATING", body.status)
    }

    @Test
    fun `push DRAFT plan then generate then pull returns READY plan with days`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val syncRepository = FakeSyncRepository()
        val planId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val recipeId = syncRepository.seedCandidateRecipe()
        val log = KtorSimpleLogger("test")
        val generationScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val generationService = MealPlanGenerationService(syncRepository, generationScope, log)

        // Seed plan directly and trigger generation
        syncRepository.upsertMealPlan(buildDraftPlan(planId), userId, kotlinx.datetime.Clock.System.now())
        generationService.startGeneration(planId, userId)

        // Advance coroutines to completion
        advanceUntilIdle()

        // Plan should now be READY with days
        val record = syncRepository.getMealPlanForUser(planId, userId)
        assertNotNull(record)
        assertEquals("READY", record.plan.status)
        assertEquals(3, record.plan.days.size)
        // 1 candidate + HIGH variety → first day is assigned, remainder are partial fills
        assertTrue(record.plan.days.any { it.dinnerRecipeId == recipeId.toString() })
    }

    @Test
    fun `soft-deleted plan is returned in pull with deletedAt set`() = testApplication {
        val syncRepository = FakeSyncRepository()

        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = syncRepository,
                userPreferencesRepository = FakeUserPreferencesRepository()
            )
        }

        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()
        val planId = UUID.randomUUID()

        // Push a soft-deleted plan
        client.post("/sync/push") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                SyncPushRequest(
                    recipes = emptyList(),
                    mealPlans = listOf(buildDraftPlan(planId, deletedAt = 2000L))
                )
            )
        }

        val pullResponse = client.get("/sync/pull?since=0&limit=100") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, pullResponse.status)
        val pullBody = pullResponse.body<SyncPullResponse>()
        val deletedPlan = pullBody.mealPlans.firstOrNull { it.uuid == planId.toString() }
        assertNotNull(deletedPlan)
        assertNotNull(deletedPlan.deletedAt)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildDraftPlan(
        planId: UUID = UUID.randomUUID(),
        updatedAt: Long = 1000L,
        deletedAt: Long? = null
    ) = SyncMealPlanDto(
        uuid = planId.toString(),
        name = "Week Plan",
        status = "DRAFT",
        preferencesJson = """{"planLengthDays":3,"mealType":"DINNER","recipeSource":"INCLUDE_PUBLIC","varietyPreference":"HIGH"}""",
        createdAt = 1000L,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        days = listOf(
            SyncMealPlanDayDto(uuid = UUID.randomUUID().toString(), dayIndex = 0, dinnerRecipeId = null, lunchRecipeId = null)
        )
    )

    private suspend fun HttpClient.registerAndGetAuth(): AuthResponse {
        val registerRequest = RegisterRequest(
            email = "mealplan-test-${UUID.randomUUID()}@example.com",
            username = "mealplanuser-${UUID.randomUUID().toString().take(8)}",
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
