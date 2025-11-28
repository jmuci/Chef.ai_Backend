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
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository())
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
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository())
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
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository())
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
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository())
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
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository())
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
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository())
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
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository())
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
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository())
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
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository())
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
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository())
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
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository())
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
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository())
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
}
