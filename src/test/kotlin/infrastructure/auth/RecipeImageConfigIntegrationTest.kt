package infrastructure.auth

import com.tenmilelabs.application.dto.AuthResponse
import com.tenmilelabs.application.dto.ImageErrorResponse
import com.tenmilelabs.application.dto.RegisterRequest
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
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val JPEG_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3, 4, 5)

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * `ImageBlobConfig` isn't a [module] parameter — the running app always loads it from
 * `environment.config` (see `Application.imageBlobConfig()`). These tests exercise the
 * config-dependent branches (kill switches, quota, upload size cap) over real HTTP by setting
 * that config directly, the way [io.ktor.server.testing.testApplication] is meant to be
 * configured — [RecipeImageServiceTest][domain.service.RecipeImageServiceTest] covers the same
 * branches at the service level with a directly-constructed `ImageBlobConfig`.
 */
class RecipeImageConfigIntegrationTest {
    @Test
    fun `scraped image upload is rejected over HTTP when the kill switch is off`() = testApplication {
        environment { config = MapApplicationConfig("imageBlob.allowScrapedImageUpload" to "false") }
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
        imageBlobRepository.seedRecipe(
            recipeId = recipeId,
            ownerId = UUID.fromString(auth.userId),
            imageUrl = "https://example.com/photo.jpg" // SCRAPED provenance
        )
        val hash = sha256Hex(JPEG_BYTES)

        val response = client.put("/recipes/$recipeId/image") {
            bearerAuth(auth.token)
            contentType(ContentType("image", "jpeg"))
            headers { append("X-Content-SHA256", hash) }
            setBody(JPEG_BYTES)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("SCRAPED_UPLOAD_DISABLED", response.body<ImageErrorResponse>().error)
    }

    @Test
    fun `a user photo still uploads over HTTP when the scraped kill switch is off`() = testApplication {
        environment { config = MapApplicationConfig("imageBlob.allowScrapedImageUpload" to "false") }
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
        // blank imageUrl => USER provenance, unaffected by the scraped-upload kill switch
        imageBlobRepository.seedRecipe(recipeId = recipeId, ownerId = UUID.fromString(auth.userId), imageUrl = "")
        val hash = sha256Hex(JPEG_BYTES)

        val response = client.put("/recipes/$recipeId/image") {
            bearerAuth(auth.token)
            contentType(ContentType("image", "jpeg"))
            headers { append("X-Content-SHA256", hash) }
            setBody(JPEG_BYTES)
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `upload over quota returns 403 QUOTA_EXCEEDED over HTTP`() = testApplication {
        environment {
            config = MapApplicationConfig("imageBlob.perUserQuotaBytes" to (JPEG_BYTES.size - 1).toString())
        }
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
        imageBlobRepository.seedRecipe(recipeId = recipeId, ownerId = UUID.fromString(auth.userId), imageUrl = "")
        val hash = sha256Hex(JPEG_BYTES)

        val response = client.put("/recipes/$recipeId/image") {
            bearerAuth(auth.token)
            contentType(ContentType("image", "jpeg"))
            headers { append("X-Content-SHA256", hash) }
            setBody(JPEG_BYTES)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("QUOTA_EXCEEDED", response.body<ImageErrorResponse>().error)
    }

    @Test
    fun `body exceeding maxUploadBytes is rejected while streaming, nothing written`() = testApplication {
        environment { config = MapApplicationConfig("imageBlob.maxUploadBytes" to "4") }
        val imageBlobRepository = FakeImageBlobRepository()
        val blobStore = FakeBlobStore()
        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = FakeSyncRepository(),
                imageBlobRepository = imageBlobRepository,
                blobStore = blobStore
            )
        }
        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.registerAndGetAuth()
        val recipeId = UUID.randomUUID()
        imageBlobRepository.seedRecipe(recipeId = recipeId, ownerId = UUID.fromString(auth.userId), imageUrl = "")
        val hash = sha256Hex(JPEG_BYTES)

        // JPEG_BYTES is 8 bytes, over the 4-byte cap — this exercises RecipeImageRoutes'
        // readBodyCapped() abort-while-streaming path, not just RecipeImageService's own
        // bytes.size check (which only ever sees a body a caller already finished streaming).
        val response = client.put("/recipes/$recipeId/image") {
            bearerAuth(auth.token)
            contentType(ContentType("image", "jpeg"))
            headers { append("X-Content-SHA256", hash) }
            setBody(JPEG_BYTES)
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertTrue(blobStore.listAllKeys().isEmpty())
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
