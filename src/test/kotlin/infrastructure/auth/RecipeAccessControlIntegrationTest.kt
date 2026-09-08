package infrastructure.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.tenmilelabs.application.dto.AuthResponse
import com.tenmilelabs.application.dto.RegisterRequest
import com.tenmilelabs.application.service.DEVELOPMENT_JWT_SECRET
import com.tenmilelabs.application.service.module
import com.tenmilelabs.infrastructure.database.FakeRecipesRepository
import com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository
import com.tenmilelabs.infrastructure.database.FakeSyncRepository
import com.tenmilelabs.infrastructure.database.FakeUserRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Route-level access control on `/recipes`, covering security audit findings F4 and F7.
 *
 * F4: `/recipes/byId` and `/recipes/byName` queried the repository directly, which applies no
 * visibility predicate, so any authenticated caller could read any other user's PRIVATE recipe.
 * F7: the handlers parsed `call.userId` with a bare `UUID.fromString`, so a token with a missing
 * or malformed `userId` claim produced a 500 instead of a 401.
 */
class RecipeAccessControlIntegrationTest {

    // ── F4: visibility ──────────────────────────────────────────────────────────

    @Test
    fun `byId does not return another user's private recipe`() = withApp { client ->
        val auth = client.registerAndGetAuth()

        val response = client.get("/recipes/byId?uuid=${FakeRecipesRepository.FOREIGN_PRIVATE_RECIPE_ID}") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        // 404 rather than 403: the endpoint must not confirm that the recipe exists.
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertFalse(response.bodyAsText().contains("Recipe 3"), "must not leak the recipe body")
    }

    @Test
    fun `byName does not return another user's private recipe`() = withApp { client ->
        val auth = client.registerAndGetAuth()

        val response = client.get("/recipes/byName?title=Recipe+3") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `byId still returns a public recipe the caller does not own`() = withApp { client ->
        val auth = client.registerAndGetAuth()

        val response = client.get("/recipes/byId?uuid=${FakeRecipesRepository.PUBLIC_RECIPE_ID}") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Recipe 1"))
    }

    @Test
    fun `byId returns the caller's own private recipe`() = withApp { client ->
        // The fake seeds recipes 1 and 2 as owned by TEST_USER_ID, which registering
        // test@example.com resolves to.
        val auth = client.registerAsTestUser()

        val response = client.get("/recipes/byId?uuid=${FakeRecipesRepository.OWNED_PRIVATE_RECIPE_ID}") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Recipe 2"))
    }

    @Test
    fun `byId rejects a malformed uuid with 400 rather than 500`() = withApp { client ->
        val auth = client.registerAndGetAuth()

        val response = client.get("/recipes/byId?uuid=not-a-uuid") {
            bearerAuth(auth.token)
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ── F7: tokens carrying no usable userId claim ──────────────────────────────

    @Test
    fun `a token with no userId claim is rejected with 401 on every recipes route`() = withApp { client ->
        val token = tokenWithoutUserId()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/recipes") { bearerAuth(token) }.status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/recipes/byId?uuid=${FakeRecipesRepository.PUBLIC_RECIPE_ID}") { bearerAuth(token) }.status
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.delete("/recipes?uuid=${FakeRecipesRepository.PUBLIC_RECIPE_ID}") { bearerAuth(token) }.status
        )
        val post = client.post("/recipes") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                """{"title":"x","description":"d","imageUrl":"","imageUrlThumbnail":"",""" +
                    """"prepTimeMinutes":1,"cookTimeMinutes":1,"servings":1,""" +
                    """"recipeExternalUrl":null,"privacy":"PRIVATE"}"""
            )
        }
        assertEquals(HttpStatusCode.Unauthorized, post.status)
    }

    @Test
    fun `a token whose userId claim is not a uuid is rejected with 401`() = withApp { client ->
        val token = signedToken { withClaim("userId", "not-a-uuid") }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/recipes") { bearerAuth(token) }.status)
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    /**
     * Signs with [DEVELOPMENT_JWT_SECRET] because `testApplication` supplies an empty config and
     * runs with `developmentMode = true`, which is the one case where `resolveJwtSecret` falls
     * back to that key. Audience and issuer are the defaults `module()` uses when unconfigured.
     */
    private fun signedToken(builder: com.auth0.jwt.JWTCreator.Builder.() -> Unit): String =
        JWT.create()
            .withAudience("jwt-audience")
            .withIssuer("http://0.0.0.0:8080")
            .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
            .apply(builder)
            .sign(Algorithm.HMAC256(DEVELOPMENT_JWT_SECRET))

    private fun tokenWithoutUserId(): String = signedToken { }

    private fun withApp(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = FakeSyncRepository()
            )
        }
        val client = createClient { install(ContentNegotiation) { json() } }
        block(client)
    }

    private suspend fun HttpClient.registerAndGetAuth(): AuthResponse = register(
        email = "outsider-${UUID.randomUUID()}@example.com",
        username = "outsider${UUID.randomUUID().toString().take(6).replace("-", "")}"
    )

    /** FakeUserRepository maps this address to TEST_USER_ID, the owner of the seeded recipes. */
    private suspend fun HttpClient.registerAsTestUser(): AuthResponse =
        register(email = "test@example.com", username = "testuser")

    private suspend fun HttpClient.register(email: String, username: String): AuthResponse {
        val response = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = email, username = username, password = "password123"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }
}
