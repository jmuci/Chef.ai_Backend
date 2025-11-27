package com.tenmilelabs.application

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.tenmilelabs.application.dto.AuthResponse
import com.tenmilelabs.application.dto.RegisterRequest
import com.tenmilelabs.application.service.module
import com.tenmilelabs.domain.model.Label
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
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ApplicationJsonPathTest {

    private suspend fun HttpClient.getAuthToken(): String {
        val registerRequest = RegisterRequest(
            email = "jsontest@example.com",
            username = "jsontestuser",
            password = "TestPassword123!"
        )

        val response = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }

        val authResponse = response.body<AuthResponse>()
        return authResponse.token
    }

    @Test
    fun recipesCanBeFound() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val token = client.getAuthToken()

        val jsonDoc = client.getAsJsonPath("/recipes", token)

        val result: List<String> = jsonDoc.read("$[*].title")
        // User1 will see their own recipes + public recipes from others
        // Should be at least 3 recipes
        assertEquals(true, result.size >= 3, "Expected at least 3 recipes, got ${result.size}: $result")
    }

    @Test
    fun recipesCanBeFoundByLabel() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository(), userRepository = FakeUserRepository())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val token = client.getAuthToken()

        val label = Label.LowCarb
        val jsonDoc = client.getAsJsonPath("/recipes/byLabel?label=$label", token)

        val result: List<String> =
            jsonDoc.read("$[?(@.label == '$label')].title")
        // User1 owns Recipe 5 (LowCarb) and can see it
        assertEquals(true, result.size >= 1) // At least one LowCarb recipe
    }

    suspend fun HttpClient.getAsJsonPath(url: String, token: String): DocumentContext {
        val response = this.get(url) {
            bearerAuth(token)
            accept(ContentType.Application.Json)
        }
        return JsonPath.parse(response.bodyAsText())
    }
}