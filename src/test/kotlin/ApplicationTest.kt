package com.tenmilelabs

import com.tenmilelabs.application.dto.*
import com.tenmilelabs.application.service.module
import com.tenmilelabs.infrastructure.database.FakeRecipesRepository
import com.tenmilelabs.infrastructure.database.FakeUserRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApplicationTest {

    // Helper function to create a test user and get JWT token
    private suspend fun HttpClient.getAuthToken(): String {
        val registerRequest = RegisterRequest(
            email = "test@example.com",
            username = "testuser",
            password = "TestPassword123!"
        )

        val registerResponse = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }

        val authResponse = registerResponse.body<AuthResponse>()
        return authResponse.token
    }

    @Test
    fun testRoot() = testApplication {
        application {
            module(
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository()
            )
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertContains(bodyAsText(), "Coming Soon")
        }
    }

    @Test
    fun recipesCanBeFoundByLabel() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val token = client.getAuthToken()

        val response = client.get("/recipes/byLabel?label=Vegetarian") {
            bearerAuth(token)
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(body, "Recipe 3")
        assertContains(body, "Description 3")
    }

    @Test
    fun recipesCanBeFoundByName() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val token = client.getAuthToken()

        val response = client.get("/recipes/byName?title=Recipe+1") {
            bearerAuth(token)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(body, "Recipe 1")
        assertContains(body, "Description 1")
    }

    @Test
    fun recipesCanBeFoundById() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val token = client.getAuthToken()

        val response = client.get("/recipes/byId?uuid=1") {
            bearerAuth(token)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(body, "Recipe 1")
        assertContains(body, "Description 1")
    }

    @Test
    fun recipesCanBeDeletedById() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val token = client.getAuthToken()

        val response = client.delete("/recipes?uuid=recipeToBeDeleted1") {
            bearerAuth(token)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.NoContent, response.status)

        val response2 = client.get("/recipes/byId?uuid=recipeToBeDeleted1") {
            bearerAuth(token)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.NotFound, response2.status)
    }

    @Test
    fun invalidLabelProduces400() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val token = client.getAuthToken()

        val response = client.get("/recipes/byLabel?label=FooBAr") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun unusedLabelProduces404() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val token = client.getAuthToken()

        val response = client.get("/recipes/byLabel/Pescatarian") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }


    @Test
    fun newRecipesCanBeAdded() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val token = client.getAuthToken()

        val response1 = client.post("/recipes") {
            bearerAuth(token)
            header(
                HttpHeaders.ContentType,
                ContentType.Application.Json
            )
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(
                CreateRecipeRequest(
                    "swimming peaches",
                    "French",
                    "Go to the beach",
                    "45",
                    "https://example.com",
                    "https://example.com"
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response1.status)

        val response2 = client.get("/recipes") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, response2.status)
        val body = response2.bodyAsText()

        assertContains(body, "swimming peaches")
        assertContains(body, "Go to the beach")
    }

    @Test
    fun postCreateRecipeRequest_withWrongLabel() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val token = client.getAuthToken()

        val response1 = client.post("/recipes") {
            bearerAuth(token)
            header(
                HttpHeaders.ContentType,
                ContentType.Application.Json
            )
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(
                CreateRecipeRequest(
                    "swimming peaches",
                    "NotExists",
                    "Go to the beach",
                    "45",
                    "https://example.com",
                    "https://example.com"
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response1.status)
    }


    @Test
    fun newRecipesFailToBeAddedWithInvalidLabel() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val token = client.getAuthToken()

        val response1 = client.post("/recipes") {
            bearerAuth(token)
            header(
                HttpHeaders.ContentType,
                ContentType.Application.FormUrlEncoded.toString()
            )
            setBody(
                listOf(
                    "title" to "swimming peaches",
                    "description" to "Go to the beach",
                    "label" to "Invalid",
                    "prepTimeMins" to "45",
                    "recipeUrl" to "https://example.com",
                    "imageUrl" to "https://example.com"
                ).formUrlEncode()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response1.status)
    }

    // Authentication Tests
    @Test
    fun userCanRegister() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val registerRequest = RegisterRequest(
            email = "newuser@example.com",
            username = "newuser",
            password = "SecurePassword123!"
        )

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }

        assertEquals(HttpStatusCode.Created, response.status)

        val authResponse = response.body<AuthResponse>()
        assertNotNull(authResponse.token)
        assertEquals("newuser@example.com", authResponse.email)
        assertEquals("newuser", authResponse.username)
    }

    @Test
    fun userCannotRegisterWithDuplicateEmail() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val registerRequest = RegisterRequest(
            email = "duplicate@example.com",
            username = "user123",
            password = "SecurePassword123!"
        )

        // First registration should succeed
        val response1 = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        assertEquals(HttpStatusCode.Created, response1.status)

        // Second registration with same email should fail
        val response2 = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest.copy(username = "user456"))
        }
        assertEquals(HttpStatusCode.BadRequest, response2.status)
    }

    @Test
    fun userCanLogin() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        // First register a user
        val registerRequest = RegisterRequest(
            email = "login@example.com",
            username = "loginuser",
            password = "SecurePassword123!"
        )
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }

        // Then try to login
        val loginRequest = LoginRequest(
            email = "login@example.com",
            password = "SecurePassword123!"
        )
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(loginRequest)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val authResponse = response.body<AuthResponse>()
        assertNotNull(authResponse.token)
        assertEquals("login@example.com", authResponse.email)
        assertEquals("loginuser", authResponse.username)
    }

    @Test
    fun userCannotLoginWithWrongPassword() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        // First register a user
        val registerRequest = RegisterRequest(
            email = "wrongpass@example.com",
            username = "wrongpassuser",
            password = "CorrectPassword123!"
        )
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }

        // Try to login with wrong password
        val loginRequest = LoginRequest(
            email = "wrongpass@example.com",
            password = "WrongPassword123!"
        )
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(loginRequest)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun unauthorizedRequestReturns401() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
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
    fun invalidTokenReturns401() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository(), refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository())
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        // Try to access protected endpoint with invalid token
        val response = client.get("/recipes") {
            bearerAuth("invalid.token.here")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

}
