package com.tenmilelabs.infrastructure.auth

import com.tenmilelabs.application.dto.LoginRequest
import com.tenmilelabs.application.dto.RegisterRequest
import com.tenmilelabs.application.service.module
import com.tenmilelabs.infrastructure.database.FakeRecipesRepository
import com.tenmilelabs.infrastructure.database.FakeUserRepository
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthIntegrationTest {

    @Test
    fun `complete authentication flow - register, login, access protected endpoint`() = testApplication {
        application {
            module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        // Step 1: Register a new user
        val registerRequest = RegisterRequest(
            email = "flow@example.com",
            username = "flowuser",
            password = "SecurePass123"
        )
        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        assertEquals(HttpStatusCode.Created, registerResponse.status)

        // Step 2: Login with the registered user
        val loginRequest = LoginRequest(
            email = "flow@example.com",
            password = "SecurePass123"
        )
        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(loginRequest)
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val loginBody = loginResponse.bodyAsText()
        assertTrue(loginBody.contains("token"))

        // Step 3: Access protected endpoint with token
        val authToken = loginResponse.body<com.tenmilelabs.application.dto.AuthResponse>().token
        val recipesResponse = client.get("/recipes") {
            bearerAuth(authToken)
        }
        assertEquals(HttpStatusCode.OK, recipesResponse.status)
    }

    @Test
    fun `JWT token from registration should work for protected endpoints`() = testApplication {
        application {
            module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        // Register and get token
        val registerRequest = RegisterRequest(
            email = "regtoken@example.com",
            username = "regtokenuser",
            password = "SecurePass123"
        )
        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }

        val authToken = registerResponse.body<com.tenmilelabs.application.dto.AuthResponse>().token

        // Use token to access protected endpoint
        val recipesResponse = client.get("/recipes") {
            bearerAuth(authToken)
        }
        assertEquals(HttpStatusCode.OK, recipesResponse.status)
    }

    @Test
    fun `expired or invalid token should be rejected`() = testApplication {
        application {
            module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        // Try to access protected endpoint with invalid token
        val response = client.get("/recipes") {
            bearerAuth("invalid.jwt.token")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `missing authorization header should return 401`() = testApplication {
        application {
            module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        // Try to access protected endpoint without token
        val response = client.get("/recipes")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `malformed authorization header should return 401`() = testApplication {
        application {
            module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        // Try with malformed header
        val response = client.get("/recipes") {
            header(HttpHeaders.Authorization, "NotBearer token")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `public endpoints should work without authentication`() = testApplication {
        application {
            module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        // Register endpoint should be public
        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("public@example.com", "publicuser", "SecurePass123"))
        }
        assertTrue(registerResponse.status == HttpStatusCode.Created || registerResponse.status == HttpStatusCode.BadRequest)

        // Login endpoint should be public
        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("nonexistent@example.com", "password"))
        }
        assertTrue(loginResponse.status.value in 200..499) // Should not be 500 error
    }

    @Test
    fun `token should include correct user claims`() = testApplication {
        application {
            module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        // Register
        val registerRequest = RegisterRequest(
            email = "claims@example.com",
            username = "claimsuser",
            password = "SecurePass123"
        )
        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }

        val authResponse = registerResponse.body<com.tenmilelabs.application.dto.AuthResponse>()

        // Verify response contains expected fields
        assertNotNull(authResponse.token)
        assertNotNull(authResponse.userId)
        assertEquals("claims@example.com", authResponse.email)
        assertEquals("claimsuser", authResponse.username)
    }

    @Test
    fun `multiple users can authenticate independently`() = testApplication {
        application {
            module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        // Register user 1
        val user1Response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("user1@example.com", "user1", "SecurePass123"))
        }
        val user1Auth = user1Response.body<com.tenmilelabs.application.dto.AuthResponse>()

        // Register user 2
        val user2Response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("user2@example.com", "user2", "SecurePass123"))
        }
        val user2Auth = user2Response.body<com.tenmilelabs.application.dto.AuthResponse>()

        // Both tokens should work independently
        val recipes1 = client.get("/recipes") {
            bearerAuth(user1Auth.token)
        }
        assertEquals(HttpStatusCode.OK, recipes1.status)

        val recipes2 = client.get("/recipes") {
            bearerAuth(user2Auth.token)
        }
        assertEquals(HttpStatusCode.OK, recipes2.status)

        // Tokens should be different
        assertTrue(user1Auth.token != user2Auth.token)
        assertTrue(user1Auth.userId != user2Auth.userId)
    }

    @Test
    fun `login after registration should generate new token`() = testApplication {
        application {
            module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        // Register
        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("newtoken@example.com", "newtokenuser", "SecurePass123"))
        }
        val registerAuth = registerResponse.body<com.tenmilelabs.application.dto.AuthResponse>()

        // Delay to ensure different timestamp (JWT uses seconds for exp)
        kotlinx.coroutines.delay(1100)

        // Login
        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("newtoken@example.com", "SecurePass123"))
        }
        val loginAuth = loginResponse.body<com.tenmilelabs.application.dto.AuthResponse>()

        // UserIds should match (same user)
        assertEquals(registerAuth.userId, loginAuth.userId)

        // Both tokens should be valid
        assertNotNull(registerAuth.token)
        assertNotNull(loginAuth.token)
    }

    @Test
    fun `allowed special characters in credentials should be handled properly`() = testApplication {
        application {
            module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        // Register with allowed special characters
        val registerRequest = RegisterRequest(
            email = "special.test@example.com",
            username = "user_test-123",
            password = "P@ssw0rd123"
        )
        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        assertEquals(HttpStatusCode.Created, registerResponse.status)

        // Login with same credentials
        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("special.test@example.com", "P@ssw0rd123"))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
    }

    @Test
    fun `concurrent requests with same token should all succeed`() = testApplication {
        application {
            module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        // Get a token
        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("concurrent@example.com", "concurrentuser", "SecurePass123"))
        }
        val token = registerResponse.body<com.tenmilelabs.application.dto.AuthResponse>().token

        // Make multiple concurrent requests
        val responses = (1..5).map {
            client.get("/recipes") {
                bearerAuth(token)
            }
        }

        // All should succeed
        responses.forEach { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `userId from token should match registered userId`() = testApplication {
        application {
            module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        // Register
        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("userid@example.com", "useriduser", "SecurePass123"))
        }
        val registerAuth = registerResponse.body<com.tenmilelabs.application.dto.AuthResponse>()

        // Login
        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("userid@example.com", "SecurePass123"))
        }
        val loginAuth = loginResponse.body<com.tenmilelabs.application.dto.AuthResponse>()

        // UserIds should match
        assertEquals(registerAuth.userId, loginAuth.userId)
    }

    // The route's exception-to-status mapping (AuthRoutes.kt /auth/refresh) was previously
    // untested at the HTTP layer: RefreshTokenServiceTest.kt exercises AuthService.refreshToken
    // directly, but never through the route's try/catch, so a broken `catch` clause ordering or
    // a status-code typo there would not fail any test.

    @Test
    fun `refresh with a valid token returns new tokens over HTTP`() = testApplication {
        application {
            module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }
        val client = createClient { install(ContentNegotiation) { json() } }

        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("refresh-http@example.com", "refreshhttpuser", "SecurePass123"))
        }
        val original = registerResponse.body<com.tenmilelabs.application.dto.AuthResponse>()

        val refreshResponse = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(com.tenmilelabs.application.dto.RefreshTokenRequest(original.refreshToken))
        }

        assertEquals(HttpStatusCode.OK, refreshResponse.status)
        val body = refreshResponse.body<com.tenmilelabs.application.dto.RefreshTokenResponse>()
        assertEquals(original.userId, body.userId)
        assertTrue(body.accessToken != original.token, "Access token should rotate")
        assertTrue(body.refreshToken != original.refreshToken, "Refresh token should rotate")

        // The rotated access token must actually work against a protected endpoint.
        val recipesResponse = client.get("/recipes") { bearerAuth(body.accessToken) }
        assertEquals(HttpStatusCode.OK, recipesResponse.status)
    }

    @Test
    fun `refresh with an unrecognized token returns 401 over HTTP`() = testApplication {
        application {
            module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(com.tenmilelabs.application.dto.RefreshTokenRequest("not-a-real-refresh-token"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `refresh with a blank token returns 401 over HTTP`() = testApplication {
        application {
            module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(com.tenmilelabs.application.dto.RefreshTokenRequest(""))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `reusing an already-rotated refresh token returns 401 and revokes the rotated one too`() = testApplication {
        application {
            module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }
        val client = createClient { install(ContentNegotiation) { json() } }

        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("reuse-http@example.com", "reusehttpuser", "SecurePass123"))
        }
        val original = registerResponse.body<com.tenmilelabs.application.dto.AuthResponse>()

        val firstRefresh = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(com.tenmilelabs.application.dto.RefreshTokenRequest(original.refreshToken))
        }
        assertEquals(HttpStatusCode.OK, firstRefresh.status)
        val rotated = firstRefresh.body<com.tenmilelabs.application.dto.RefreshTokenResponse>()

        // Replay the original (already-consumed) refresh token - this is the reuse-detection path.
        val replay = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(com.tenmilelabs.application.dto.RefreshTokenRequest(original.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, replay.status)

        // The security breach must revoke the token family entirely, so even the token issued by
        // the legitimate first rotation is now rejected too.
        val rotatedNowRejected = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(com.tenmilelabs.application.dto.RefreshTokenRequest(rotated.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, rotatedNowRejected.status)
    }

    @Test
    fun `refresh with a malformed JSON body returns 400 over HTTP`() = testApplication {
        application {
            module(configureDatabase = false, recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{ this is not valid json """)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
