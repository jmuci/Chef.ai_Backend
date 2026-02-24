package infrastructure.auth

import com.tenmilelabs.application.dto.AuthResponse
import com.tenmilelabs.application.dto.RegisterRequest
import com.tenmilelabs.application.dto.SyncPushRequest
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
import kotlin.test.assertNotNull

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
        updatedAt: Long
    ): SyncRecipe = SyncRecipe(
        uuid = recipeId.toString(),
        title = "Carbonara",
        description = "Classic",
        imageUrl = "https://example.com/image.jpg",
        imageUrlThumbnail = "https://example.com/thumb.jpg",
        prepTimeMinutes = 10,
        cookTimeMinutes = 20,
        servings = 2,
        creatorId = creatorId,
        recipeExternalUrl = null,
        privacy = "PRIVATE",
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
