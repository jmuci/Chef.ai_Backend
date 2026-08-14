package infrastructure.auth

import com.tenmilelabs.application.dto.AuthResponse
import com.tenmilelabs.application.dto.RegisterRequest
import com.tenmilelabs.application.dto.SyncPullResponse
import com.tenmilelabs.application.dto.SyncPushRequest
import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.application.service.module
import com.tenmilelabs.infrastructure.database.FakeImageBlobRepository
import com.tenmilelabs.infrastructure.database.FakeRecipesRepository
import com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository
import com.tenmilelabs.infrastructure.database.FakeSyncRepository
import com.tenmilelabs.infrastructure.database.FakeUserRepository
import com.tenmilelabs.infrastructure.storage.FakeBlobStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val JPEG_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3, 4, 5)

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * Covers imageBlobId's presence in the sync protocol itself (SyncDtos.kt) — the part of
 * RecipeImageRoutesIntegrationTest's original scope that only compiles once SyncRecipe carries
 * the field. See that file's class doc for why these tests seed FakeSyncRepository by hand
 * after each image mutation: the fakes don't share a table the way the real
 * PostgresSyncRepository/PostgresImageBlobRepository do.
 */
class RecipeImageSyncIntegrationTest {
    @Test
    fun `full lifecycle - push, upload, pull sees imageBlobId, GET returns the bytes`() = testApplication {
        val syncRepository = FakeSyncRepository()
        val imageBlobRepository = FakeImageBlobRepository()
        val blobStore = FakeBlobStore()

        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = syncRepository,
                imageBlobRepository = imageBlobRepository,
                blobStore = blobStore
            )
        }

        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()
        val recipeId = UUID.randomUUID()

        val recipe = sampleRecipe(recipeId, auth.userId)
        val pushResponse = client.post("/sync/push") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(SyncPushRequest(recipes = listOf(recipe)))
        }
        assertEquals(HttpStatusCode.OK, pushResponse.status)
        imageBlobRepository.seedRecipe(
            recipeId = recipeId,
            ownerId = UUID.fromString(auth.userId),
            privacy = recipe.privacy,
            imageUrl = recipe.imageUrl,
            imageBlobId = null,
            updatedAtMillis = recipe.updatedAt
        )

        val hash = sha256Hex(JPEG_BYTES)
        val uploadResponse = client.put("/recipes/$recipeId/image") {
            bearerAuth(auth.token)
            contentType(ContentType("image", "jpeg"))
            headers { append("X-Content-SHA256", hash) }
            setBody(JPEG_BYTES)
        }
        assertEquals(HttpStatusCode.OK, uploadResponse.status)
        // See the class doc: the two fakes don't share a table, so mirror the write by hand.
        syncRepository.setImageBlobId(recipeId, hash)

        val pullResponse = client.get("/sync/pull?since=0&limit=10") {
            bearerAuth(auth.token)
        }
        assertEquals(HttpStatusCode.OK, pullResponse.status)
        val pulled = pullResponse.body<SyncPullResponse>().recipes.single { it.uuid == recipeId.toString() }
        assertEquals(hash, pulled.imageBlobId)

        val getResponse = client.get("/recipes/$recipeId/image") {
            bearerAuth(auth.token)
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)
        assertTrue(JPEG_BYTES.contentEquals(getResponse.bodyAsBytes()))
        assertEquals("\"$hash\"", getResponse.headers[HttpHeaders.ETag])
    }

    @Test
    fun `push never lets the client set imageBlobId directly`() = testApplication {
        val syncRepository = FakeSyncRepository()
        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = syncRepository
            )
        }
        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()
        val recipeId = UUID.randomUUID()

        val response = client.post("/sync/push") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(
                SyncPushRequest(
                    recipes = listOf(sampleRecipe(recipeId, auth.userId).copy(imageBlobId = "client-supplied-hash"))
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(syncRepository.getRecipe(recipeId)?.recipe?.imageBlobId)
    }

    private fun sampleRecipe(recipeId: UUID, creatorId: String): SyncRecipe = SyncRecipe(
        uuid = recipeId.toString(),
        title = "Carbonara",
        description = "Classic",
        imageUrl = "",
        imageUrlThumbnail = "",
        prepTimeMinutes = 10,
        cookTimeMinutes = 20,
        servings = 2,
        creatorId = creatorId,
        recipeExternalUrl = null,
        privacy = "PRIVATE",
        updatedAt = System.currentTimeMillis(),
        deletedAt = null,
        steps = emptyList(),
        ingredients = emptyList(),
        tagIds = emptyList(),
        labelIds = emptyList()
    )

    private suspend fun HttpClient.registerAndGetAuth(): AuthResponse {
        val email = "user-${UUID.randomUUID()}@example.com"
        val response = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    email = email,
                    username = "user-${UUID.randomUUID().toString().take(8)}",
                    password = "password123"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }
}
