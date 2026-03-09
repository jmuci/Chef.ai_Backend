package infrastructure.auth

import com.tenmilelabs.application.dto.AuthResponse
import com.tenmilelabs.application.dto.RegisterRequest
import com.tenmilelabs.application.dto.SyncPushRequest
import com.tenmilelabs.application.dto.SyncPullResponse
import com.tenmilelabs.application.dto.SyncPushResponse
import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.application.dto.SyncRecipeIngredient
import com.tenmilelabs.application.dto.SyncRecipeStep
import com.tenmilelabs.application.dto.ConflictReasons
import com.tenmilelabs.application.dto.SyncErrors
import com.tenmilelabs.application.service.module
import com.tenmilelabs.infrastructure.database.FakeRecipesRepository
import com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository
import com.tenmilelabs.infrastructure.database.FakeSyncRepository
import com.tenmilelabs.infrastructure.database.FakeUserRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncRoutesIntegrationTest {
    @Test
    fun pushRouteAcceptsValidRecipeAggregate() = testApplication {
        val syncRepository = FakeSyncRepository()
        val ingredientId = syncRepository.seedIngredient()

        application {
            module(
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = syncRepository
            )
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val auth = client.registerAndGetAuth()
        val recipeId = UUID.randomUUID()
        val stepId = UUID.randomUUID()

        val response = client.post("/sync/push") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                SyncPushRequest(
                    recipes = listOf(
                        SyncRecipe(
                            uuid = recipeId.toString(),
                            title = "Carbonara",
                            description = "Classic",
                            imageUrl = "https://example.com/image.jpg",
                            imageUrlThumbnail = "https://example.com/thumb.jpg",
                            prepTimeMinutes = 10,
                            cookTimeMinutes = 20,
                            servings = 2,
                            creatorId = auth.userId,
                            recipeExternalUrl = null,
                            privacy = "PRIVATE",
                            updatedAt = System.currentTimeMillis(),
                            deletedAt = null,
                            steps = listOf(
                                SyncRecipeStep(
                                    uuid = stepId.toString(),
                                    orderIndex = 0,
                                    instruction = "Boil water"
                                )
                            ),
                            ingredients = listOf(
                                SyncRecipeIngredient(
                                    ingredientId = ingredientId.toString(),
                                    quantity = 200.0,
                                    unit = "g"
                                )
                            ),
                            tagIds = emptyList(),
                            labelIds = emptyList()
                        )
                    )
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<SyncPushResponse>()
        assertEquals(1, body.accepted.size)
        assertEquals(0, body.conflicts.size)
        assertEquals(0, body.errors.size)
        assertNotNull(syncRepository.getRecipe(recipeId))
    }

    @Test
    fun pushRouteReturnsConflictWhenServerVersionIsNewer() = testApplication {
        val syncRepository = FakeSyncRepository()
        val ingredientId = syncRepository.seedIngredient()

        application {
            module(
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = syncRepository
            )
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val auth = client.registerAndGetAuth()
        val recipeId = UUID.randomUUID()
        val existingServerRecipe = buildRecipe(
            recipeId = recipeId,
            creatorId = auth.userId,
            ingredientId = ingredientId,
            updatedAt = 5000L
        )
        syncRepository.seedRecipe(existingServerRecipe, serverUpdatedAtMillis = 5000L)

        val staleClientRecipe = buildRecipe(
            recipeId = recipeId,
            creatorId = auth.userId,
            ingredientId = ingredientId,
            updatedAt = 1000L
        )

        val response = client.post("/sync/push") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(SyncPushRequest(recipes = listOf(staleClientRecipe)))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<SyncPushResponse>()
        assertEquals(0, body.accepted.size)
        assertEquals(1, body.conflicts.size)
        assertEquals(0, body.errors.size)
        assertEquals(ConflictReasons.SERVER_NEWER, body.conflicts.first().reason)
    }

    @Test
    fun pushRouteReturnsMixedAcceptedConflictsAndErrors() = testApplication {
        val syncRepository = FakeSyncRepository()
        val ingredientId = syncRepository.seedIngredient()

        application {
            module(
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = syncRepository
            )
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val auth = client.registerAndGetAuth()
        val serverRecipeId = UUID.randomUUID()
        syncRepository.seedRecipe(
            buildRecipe(
                recipeId = serverRecipeId,
                creatorId = auth.userId,
                ingredientId = ingredientId,
                updatedAt = 8000L
            ),
            serverUpdatedAtMillis = 8000L
        )

        val acceptedRecipe = buildRecipe(
            recipeId = UUID.randomUUID(),
            creatorId = auth.userId,
            ingredientId = ingredientId,
            updatedAt = 9000L
        )
        val conflictRecipe = buildRecipe(
            recipeId = serverRecipeId,
            creatorId = auth.userId,
            ingredientId = ingredientId,
            updatedAt = 1000L
        )
        val errorRecipe = buildRecipe(
            recipeId = UUID.randomUUID(),
            creatorId = UUID.randomUUID().toString(),
            ingredientId = ingredientId,
            updatedAt = 9000L
        )

        val response = client.post("/sync/push") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(SyncPushRequest(recipes = listOf(acceptedRecipe, conflictRecipe, errorRecipe)))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<SyncPushResponse>()
        assertEquals(1, body.accepted.size)
        assertEquals(1, body.conflicts.size)
        assertEquals(1, body.errors.size)
        assertEquals(ConflictReasons.SERVER_NEWER, body.conflicts.first().reason)
        assertEquals(SyncErrors.CREATOR_MISMATCH, body.errors.first().reason)
    }

    @Test
    fun pushRouteIgnoresUnknownTagAndLabelIds() = testApplication {
        val syncRepository = FakeSyncRepository()
        val ingredientId = syncRepository.seedIngredient()

        application {
            module(
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = syncRepository
            )
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val auth = client.registerAndGetAuth()
        val recipe = buildRecipe(
            recipeId = UUID.randomUUID(),
            creatorId = auth.userId,
            ingredientId = ingredientId,
            updatedAt = 1000L
        ).copy(
            tagIds = listOf(UUID.randomUUID().toString()),
            labelIds = listOf(UUID.randomUUID().toString())
        )

        val response = client.post("/sync/push") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(SyncPushRequest(recipes = listOf(recipe)))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<SyncPushResponse>()
        assertEquals(1, body.accepted.size)
        assertEquals(0, body.conflicts.size)
        assertEquals(0, body.errors.size)
    }

    @Test
    fun pullRouteReturnsOnlyAccessibleDeltas() = testApplication {
        val syncRepository = FakeSyncRepository()
        val ingredientId = syncRepository.seedIngredient()

        application {
            module(
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = syncRepository
            )
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val auth = client.registerAndGetAuth()
        val ownRecipe = buildRecipe(UUID.randomUUID(), auth.userId, ingredientId, 1100L, "PRIVATE")
        val publicForeign = buildRecipe(UUID.randomUUID(), UUID.randomUUID().toString(), ingredientId, 1200L, "PUBLIC")
        val privateForeign = buildRecipe(UUID.randomUUID(), UUID.randomUUID().toString(), ingredientId, 1300L, "PRIVATE")

        syncRepository.seedRecipe(ownRecipe, 1100L)
        syncRepository.seedRecipe(publicForeign, 1200L)
        syncRepository.seedRecipe(privateForeign, 1300L)

        val response = client.get("/sync/pull?since=1000&limit=10") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<SyncPullResponse>()
        assertEquals(2, body.recipes.size)
        assertFalse(body.hasMore)
        val returnedIds = body.recipes.map { it.uuid }.toSet()
        assertTrue(returnedIds.contains(ownRecipe.uuid))
        assertTrue(returnedIds.contains(publicForeign.uuid))
        assertFalse(returnedIds.contains(privateForeign.uuid))
        val pageCreatorIds = body.recipes.map { it.creatorId }.toSet()
        val returnedCreatorIds = body.creators.map { it.uuid }.toSet()
        assertEquals(pageCreatorIds, returnedCreatorIds)
    }

    @Test
    fun pullRouteSupportsPaginationCursor() = testApplication {
        val syncRepository = FakeSyncRepository()
        val ingredientId = syncRepository.seedIngredient()

        application {
            module(
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = syncRepository
            )
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val auth = client.registerAndGetAuth()
        val r1 = buildRecipe(UUID.randomUUID(), auth.userId, ingredientId, 1000L, "PRIVATE")
        val r2 = buildRecipe(UUID.randomUUID(), auth.userId, ingredientId, 2000L, "PRIVATE")
        val r3 = buildRecipe(UUID.randomUUID(), auth.userId, ingredientId, 3000L, "PRIVATE")
        syncRepository.seedRecipe(r1, 1000L)
        syncRepository.seedRecipe(r2, 2000L)
        syncRepository.seedRecipe(r3, 3000L)

        val firstPageResponse = client.get("/sync/pull?since=0&limit=2") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, firstPageResponse.status)
        val firstPage = firstPageResponse.body<SyncPullResponse>()
        assertEquals(2, firstPage.recipes.size)
        assertTrue(firstPage.hasMore)
        assertEquals(2000L, firstPage.serverTimestamp)
        assertEquals(1, firstPage.creators.size)
        assertEquals(auth.userId, firstPage.creators.first().uuid)

        val secondPageResponse = client.get("/sync/pull?since=${firstPage.serverTimestamp}&limit=2") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, secondPageResponse.status)
        val secondPage = secondPageResponse.body<SyncPullResponse>()
        assertEquals(1, secondPage.recipes.size)
        assertFalse(secondPage.hasMore)
        assertEquals(3000L, secondPage.serverTimestamp)
        assertEquals(1, secondPage.creators.size)
        assertEquals(auth.userId, secondPage.creators.first().uuid)
    }

    /**
     * Example 1 (integration): A tag whose server_updated_at predates `since` must appear in
     * response.tags when a recipe returned in the page references it. Confirms the gap clause
     * fires end-to-end through the HTTP layer.
     */
    @Test
    fun pullRouteIncludesGapTagReferencedByReturnedRecipe() = testApplication {
        val syncRepository = FakeSyncRepository()
        val ingredientId = syncRepository.seedIngredient()
        val tagId = syncRepository.seedTag(serverUpdatedAt = 500L) // predates since=1000

        application {
            module(
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = syncRepository
            )
        }

        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()

        val recipe = buildRecipe(UUID.randomUUID(), auth.userId, ingredientId, 2000L)
            .copy(tagIds = listOf(tagId.toString()))
        syncRepository.seedRecipe(recipe, serverUpdatedAtMillis = 2000L)

        val response = client.get("/sync/pull?since=1000&limit=10") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<SyncPullResponse>()
        assertEquals(1, body.recipes.size)
        assertEquals(1, body.tags.size)
        assertEquals(tagId.toString(), body.tags.first().uuid)
        assertEquals(1, body.creators.size)
        assertEquals(auth.userId, body.creators.first().uuid)
    }

    /**
     * Example 2 (integration): When a push conflict is returned, response.referenceData must
     * contain the ingredient referenced by the conflict's serverVersion so the client can
     * resolve it without a separate pull.
     */
    @Test
    fun pushRouteConflictReferenceDataContainsServerVersionIngredient() = testApplication {
        val syncRepository = FakeSyncRepository()
        val ingredientId = syncRepository.seedIngredient()

        application {
            module(
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = syncRepository
            )
        }

        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()

        val recipeId = UUID.randomUUID()
        val serverRecipe = buildRecipe(recipeId, auth.userId, ingredientId, updatedAt = 5000L)
        syncRepository.seedRecipe(serverRecipe, serverUpdatedAtMillis = 5000L)

        val staleClientRecipe = buildRecipe(recipeId, auth.userId, ingredientId, updatedAt = 1000L)

        val response = client.post("/sync/push") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(SyncPushRequest(recipes = listOf(staleClientRecipe)))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<SyncPushResponse>()
        assertEquals(1, body.conflicts.size)
        assertEquals(ConflictReasons.SERVER_NEWER, body.conflicts.first().reason)
        val referenceIngredientIds = body.referenceData.ingredients.map { it.uuid }.toSet()
        assertTrue(referenceIngredientIds.contains(ingredientId.toString()))
    }

    /**
     * Full sync lifecycle: initial pull → Device A edit accepted → Device B conflict →
     * Device B resolution accepted → final pull confirms canonical state.
     *
     * Verifies the end-to-end protocol a mobile client would follow:
     * 1. Device B syncs baseline ("Pasta")
     * 2. Device A pushes a newer version ("Carbonara") — accepted
     * 3. Device B, unaware of Device A's change, pushes a stale version ("Mac and Cheese") — conflict
     *    • conflict.serverVersion must contain "Carbonara" (not "Mac and Cheese")
     *    • referenceData must include all FK dependencies so Device B can apply the fix locally
     * 4. Device B accepts the server's truth and re-pushes "Carbonara" with a fresh timestamp — accepted
     * 5. Final pull from Device B's prior cursor confirms "Carbonara" is the canonical recipe
     */
    @Test
    fun fullSyncLifecycleWithConflictAndResolution() = testApplication {
        val syncRepository = FakeSyncRepository()
        val ingredientId = syncRepository.seedIngredient(serverUpdatedAt = 100L)
        val tagId = syncRepository.seedTag(serverUpdatedAt = 100L)

        application {
            module(
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = syncRepository
            )
        }

        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()
        val sharedRecipeId = UUID.randomUUID()

        // Seed the initial "Pasta" recipe — shared baseline between both devices.
        val pastaRecipe = buildRecipe(
            recipeId = sharedRecipeId,
            creatorId = auth.userId,
            ingredientId = ingredientId,
            updatedAt = 500L,
            title = "Pasta"
        ).copy(tagIds = listOf(tagId.toString()))
        syncRepository.seedRecipe(pastaRecipe, serverUpdatedAtMillis = 500L)

        // ── Step 1: Device B initial pull (since=0) ──────────────────────────────────
        // Simulates Device B's first sync. Must receive "Pasta" plus all FK dependencies.
        val deviceBInitialPull = client.get("/sync/pull?since=0&limit=10") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, deviceBInitialPull.status)
        val deviceBBaseline = deviceBInitialPull.body<SyncPullResponse>()
        assertEquals(1, deviceBBaseline.recipes.size)
        assertEquals("Pasta", deviceBBaseline.recipes.first().title)
        assertTrue(deviceBBaseline.ingredients.any { it.uuid == ingredientId.toString() })
        assertTrue(deviceBBaseline.tags.any { it.uuid == tagId.toString() })
        assertTrue(deviceBBaseline.creators.any { it.uuid == auth.userId })
        val deviceBCursor = deviceBBaseline.serverTimestamp // 500L

        // ── Step 2: Device A pushes "Carbonara" edit ─────────────────────────────────
        // updatedAt=1000L > server's serverUpdatedAtMillis=500L → accepted.
        val carbonaraRecipe = buildRecipe(
            recipeId = sharedRecipeId,
            creatorId = auth.userId,
            ingredientId = ingredientId,
            updatedAt = 1000L,
            title = "Carbonara"
        ).copy(tagIds = listOf(tagId.toString()))
        val deviceAPushResponse = client.post("/sync/push") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(SyncPushRequest(recipes = listOf(carbonaraRecipe)))
        }
        assertEquals(HttpStatusCode.OK, deviceAPushResponse.status)
        val deviceAPushBody = deviceAPushResponse.body<SyncPushResponse>()
        assertEquals(1, deviceAPushBody.accepted.size)
        assertEquals(0, deviceAPushBody.conflicts.size)
        val carbonaraServerUpdatedAt = deviceAPushBody.accepted.first().serverUpdatedAt

        // ── Step 3: Device B pushes stale "Mac and Cheese" ───────────────────────────
        // updatedAt=800L < carbonaraServerUpdatedAt (≈ now) → conflict.
        val macAndCheeseRecipe = buildRecipe(
            recipeId = sharedRecipeId,
            creatorId = auth.userId,
            ingredientId = ingredientId,
            updatedAt = 800L,
            title = "Mac and Cheese"
        ).copy(tagIds = listOf(tagId.toString()))
        val deviceBConflictResponse = client.post("/sync/push") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(SyncPushRequest(recipes = listOf(macAndCheeseRecipe)))
        }
        assertEquals(HttpStatusCode.OK, deviceBConflictResponse.status)
        val deviceBConflictBody = deviceBConflictResponse.body<SyncPushResponse>()
        assertEquals(0, deviceBConflictBody.accepted.size)
        assertEquals(1, deviceBConflictBody.conflicts.size)
        assertEquals(0, deviceBConflictBody.errors.size)

        val conflict = deviceBConflictBody.conflicts.first()
        assertEquals(ConflictReasons.SERVER_NEWER, conflict.reason)
        // The server holds "Carbonara" — not Device B's "Mac and Cheese"
        assertEquals("Carbonara", conflict.serverVersion.title)
        // referenceData must include all FK deps so Device B can apply the resolution without a separate pull
        assertTrue(deviceBConflictBody.referenceData.ingredients.any { it.uuid == ingredientId.toString() })
        assertTrue(deviceBConflictBody.referenceData.tags.any { it.uuid == tagId.toString() })

        // ── Step 4: Device B resolves conflict ───────────────────────────────────────
        // Accept the server's "Carbonara" as truth. updatedAt must exceed the server's
        // previously accepted timestamp to avoid triggering another conflict.
        val resolvedRecipe = conflict.serverVersion.copy(updatedAt = carbonaraServerUpdatedAt + 1)
        val deviceBResolutionResponse = client.post("/sync/push") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(SyncPushRequest(recipes = listOf(resolvedRecipe)))
        }
        assertEquals(HttpStatusCode.OK, deviceBResolutionResponse.status)
        val deviceBResolutionBody = deviceBResolutionResponse.body<SyncPushResponse>()
        assertEquals(1, deviceBResolutionBody.accepted.size)
        assertEquals(0, deviceBResolutionBody.conflicts.size)
        assertEquals(0, deviceBResolutionBody.errors.size)

        // ── Step 5: Final pull from Device B's prior cursor ──────────────────────────
        // Device B resumes from the baseline checkpoint. "Carbonara" is the canonical state.
        val finalPull = client.get("/sync/pull?since=$deviceBCursor&limit=10") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, finalPull.status)
        val finalBody = finalPull.body<SyncPullResponse>()
        assertEquals(1, finalBody.recipes.size)
        assertEquals("Carbonara", finalBody.recipes.first().title)
        assertFalse(finalBody.hasMore)
        assertTrue(finalBody.creators.any { it.uuid == auth.userId })
    }

    @Test
    fun pullRouteRequiresSinceParameter() = testApplication {
        val syncRepository = FakeSyncRepository()

        application {
            module(
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = syncRepository
            )
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val auth = client.registerAndGetAuth()

        val response = client.get("/sync/pull") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    private suspend fun HttpClient.registerAndGetAuth(): AuthResponse {
        val registerRequest = RegisterRequest(
            email = "sync-test@example.com",
            username = "syncuser",
            password = "TestPassword123!"
        )

        val registerResponse = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        assertEquals(HttpStatusCode.Created, registerResponse.status)
        return registerResponse.body()
    }

    private fun buildRecipe(
        recipeId: UUID,
        creatorId: String,
        ingredientId: UUID,
        updatedAt: Long,
        privacy: String = "PRIVATE",
        title: String = "Carbonara"
    ): SyncRecipe = SyncRecipe(
        uuid = recipeId.toString(),
        title = title,
        description = "Classic",
        imageUrl = "https://example.com/image.jpg",
        imageUrlThumbnail = "https://example.com/thumb.jpg",
        prepTimeMinutes = 10,
        cookTimeMinutes = 20,
        servings = 2,
        creatorId = creatorId,
        recipeExternalUrl = null,
        privacy = privacy,
        updatedAt = updatedAt,
        deletedAt = null,
        steps = listOf(
            SyncRecipeStep(
                uuid = UUID.randomUUID().toString(),
                orderIndex = 0,
                instruction = "Boil water"
            )
        ),
        ingredients = listOf(
            SyncRecipeIngredient(
                ingredientId = ingredientId.toString(),
                quantity = 200.0,
                unit = "g"
            )
        ),
        tagIds = emptyList(),
        labelIds = emptyList()
    )
}
