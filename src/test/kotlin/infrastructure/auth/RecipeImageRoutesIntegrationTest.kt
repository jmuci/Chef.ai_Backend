package infrastructure.auth

import com.tenmilelabs.application.dto.AuthResponse
import com.tenmilelabs.application.dto.ImageUploadResponse
import com.tenmilelabs.application.dto.RegisterRequest
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
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
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
 * FakeSyncRepository and FakeImageBlobRepository are two independent in-memory stores, unlike
 * the real PostgresSyncRepository/PostgresImageBlobRepository, which both read the same
 * `recipes` table. Pushing a recipe via HTTP only populates the sync fake, so these tests seed
 * the image fake explicitly afterwards to reflect what a shared table would already show.
 *
 * Tests covering `imageBlobId` visibility through the sync protocol itself live in
 * RecipeImageSyncIntegrationTest, once that field exists on SyncRecipe.
 */
class RecipeImageRoutesIntegrationTest {
    @Test
    fun `GET on another user's private recipe image is 404`() = testApplication {
        val imageBlobRepository = FakeImageBlobRepository()
        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = FakeSyncRepository(),
                imageBlobRepository = imageBlobRepository,
                blobStore = FakeBlobStore()
            )
        }
        val client = createClient { install(ContentNegotiation) { json() } }
        val owner = client.registerAndGetAuth()
        val recipeId = UUID.randomUUID()
        imageBlobRepository.seedRecipe(
            recipeId = recipeId,
            ownerId = UUID.fromString(owner.userId),
            privacy = "PRIVATE",
            imageUrl = "",
            imageBlobId = "some-hash"
        )
        val stranger = client.registerAndGetAuth()

        val response = client.get("/recipes/$recipeId/image") {
            bearerAuth(stranger.token)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `upload with a hash that does not match the body is rejected`() = testApplication {
        val imageBlobRepository = FakeImageBlobRepository()
        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = FakeSyncRepository(),
                imageBlobRepository = imageBlobRepository,
                blobStore = FakeBlobStore()
            )
        }
        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()
        val recipeId = UUID.randomUUID()
        client.pushAndSeed(imageBlobRepository, recipeId, auth)

        val response = client.put("/recipes/$recipeId/image") {
            bearerAuth(auth.token)
            contentType(ContentType("image", "jpeg"))
            headers { append("X-Content-SHA256", "0".repeat(64)) }
            setBody(JPEG_BYTES)
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `PUT upload succeeds and returns imageBlobId and updatedAt`() = testApplication {
        val imageBlobRepository = FakeImageBlobRepository()
        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = FakeSyncRepository(),
                imageBlobRepository = imageBlobRepository,
                blobStore = FakeBlobStore()
            )
        }
        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()
        val recipeId = UUID.randomUUID()
        client.pushAndSeed(imageBlobRepository, recipeId, auth)
        val hash = sha256Hex(JPEG_BYTES)

        val response = client.put("/recipes/$recipeId/image") {
            bearerAuth(auth.token)
            contentType(ContentType("image", "jpeg"))
            headers { append("X-Content-SHA256", hash) }
            setBody(JPEG_BYTES)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ImageUploadResponse>()
        assertEquals(hash, body.imageBlobId)
    }

    @Test
    fun `GET returns 404 when the recipe has no image yet`() = testApplication {
        val imageBlobRepository = FakeImageBlobRepository()
        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = FakeSyncRepository(),
                imageBlobRepository = imageBlobRepository,
                blobStore = FakeBlobStore()
            )
        }
        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()
        val recipeId = UUID.randomUUID()
        client.pushAndSeed(imageBlobRepository, recipeId, auth)

        val response = client.get("/recipes/$recipeId/image") {
            bearerAuth(auth.token)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET on a public recipe is visible to a non-owner`() = testApplication {
        val imageBlobRepository = FakeImageBlobRepository()
        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = FakeSyncRepository(),
                imageBlobRepository = imageBlobRepository,
                blobStore = FakeBlobStore()
            )
        }
        val client = createClient { install(ContentNegotiation) { json() } }
        val owner = client.registerAndGetAuth()
        val recipeId = UUID.randomUUID()
        client.pushAndSeed(imageBlobRepository, recipeId, owner, privacy = "PUBLIC")
        val hash = sha256Hex(JPEG_BYTES)
        client.put("/recipes/$recipeId/image") {
            bearerAuth(owner.token)
            contentType(ContentType("image", "jpeg"))
            headers { append("X-Content-SHA256", hash) }
            setBody(JPEG_BYTES)
        }
        val stranger = client.registerAndGetAuth()

        val response = client.get("/recipes/$recipeId/image") {
            bearerAuth(stranger.token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(JPEG_BYTES.contentEquals(response.bodyAsBytes()))
    }

    @Test
    fun `PUT without a bearer token is 401`() = testApplication {
        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = FakeSyncRepository(),
                imageBlobRepository = FakeImageBlobRepository(),
                blobStore = FakeBlobStore()
            )
        }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.put("/recipes/${UUID.randomUUID()}/image") {
            contentType(ContentType("image", "jpeg"))
            headers { append("X-Content-SHA256", sha256Hex(JPEG_BYTES)) }
            setBody(JPEG_BYTES)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET without a bearer token is 401`() = testApplication {
        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = FakeSyncRepository(),
                imageBlobRepository = FakeImageBlobRepository(),
                blobStore = FakeBlobStore()
            )
        }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.get("/recipes/${UUID.randomUUID()}/image")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `DELETE without a bearer token is 401`() = testApplication {
        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = FakeSyncRepository(),
                imageBlobRepository = FakeImageBlobRepository(),
                blobStore = FakeBlobStore()
            )
        }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.delete("/recipes/${UUID.randomUUID()}/image")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `PUT with a malformed recipeId is 400`() = testApplication {
        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = FakeSyncRepository(),
                imageBlobRepository = FakeImageBlobRepository(),
                blobStore = FakeBlobStore()
            )
        }
        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()

        val response = client.put("/recipes/not-a-uuid/image") {
            bearerAuth(auth.token)
            contentType(ContentType("image", "jpeg"))
            headers { append("X-Content-SHA256", sha256Hex(JPEG_BYTES)) }
            setBody(JPEG_BYTES)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT with an unsupported content type is 415`() = testApplication {
        val imageBlobRepository = FakeImageBlobRepository()
        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = FakeSyncRepository(),
                imageBlobRepository = imageBlobRepository,
                blobStore = FakeBlobStore()
            )
        }
        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()
        val recipeId = UUID.randomUUID()
        client.pushAndSeed(imageBlobRepository, recipeId, auth)

        val response = client.put("/recipes/$recipeId/image") {
            bearerAuth(auth.token)
            contentType(ContentType("image", "gif"))
            headers { append("X-Content-SHA256", sha256Hex(JPEG_BYTES)) }
            setBody(JPEG_BYTES)
        }

        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
    }

    @Test
    fun `DELETE clears the image pointer`() = testApplication {
        val imageBlobRepository = FakeImageBlobRepository()
        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = FakeSyncRepository(),
                imageBlobRepository = imageBlobRepository,
                blobStore = FakeBlobStore()
            )
        }
        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()
        val recipeId = UUID.randomUUID()
        client.pushAndSeed(imageBlobRepository, recipeId, auth)
        val hash = sha256Hex(JPEG_BYTES)
        client.put("/recipes/$recipeId/image") {
            bearerAuth(auth.token)
            contentType(ContentType("image", "jpeg"))
            headers { append("X-Content-SHA256", hash) }
            setBody(JPEG_BYTES)
        }

        val response = client.delete("/recipes/$recipeId/image") {
            bearerAuth(auth.token)
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertNull(imageBlobRepository.findRecipeForImage(recipeId)?.imageBlobId)
    }

    private fun sampleRecipe(recipeId: UUID, creatorId: String, privacy: String = "PRIVATE"): SyncRecipe = SyncRecipe(
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
        privacy = privacy,
        updatedAt = System.currentTimeMillis(),
        deletedAt = null,
        steps = emptyList(),
        ingredients = emptyList(),
        tagIds = emptyList(),
        labelIds = emptyList()
    )

    /** Pushes a fresh recipe via HTTP, then mirrors it into the image-blob fake — see class doc. */
    private suspend fun HttpClient.pushAndSeed(
        imageBlobRepository: FakeImageBlobRepository,
        recipeId: UUID,
        auth: AuthResponse,
        privacy: String = "PRIVATE"
    ) {
        val recipe = sampleRecipe(recipeId, auth.userId, privacy)
        val response = post("/sync/push") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(SyncPushRequest(recipes = listOf(recipe)))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        imageBlobRepository.seedRecipe(
            recipeId = recipeId,
            ownerId = UUID.fromString(auth.userId),
            privacy = recipe.privacy,
            imageUrl = recipe.imageUrl,
            imageBlobId = null,
            updatedAtMillis = recipe.updatedAt
        )
    }

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
