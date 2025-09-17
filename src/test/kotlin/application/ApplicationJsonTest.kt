package com.tenmilelabs.application

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.tenmilelabs.application.service.module
import com.tenmilelabs.domain.model.Label
import com.tenmilelabs.infrastructure.database.FakeRecipesRepository
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ApplicationJsonPathTest {
    @Test
    fun recipesCanBeFound() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository)
        }
        val jsonDoc = client.getAsJsonPath("/recipes")

        val result: List<String> = jsonDoc.read("$[*].title")
        assertEquals("Recipe 1", result[0])
        assertEquals("Recipe 2", result[1])
        assertEquals("Recipe 3", result[2])
    }

    @Test
    fun recipesCanBeFoundByLabel() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository)
        }
        val label = Label.LowCarb
        val jsonDoc = client.getAsJsonPath("/recipes/byLabel?label=$label")

        val result: List<String> =
            jsonDoc.read("$[?(@.label == '$label')].title")
        assertEquals(2, result.size)

        assertEquals("Recipe 4", result[0])
        assertEquals("Recipe 5", result[1])
    }

    suspend fun HttpClient.getAsJsonPath(url: String): DocumentContext {
        val response = this.get(url) {
            accept(ContentType.Application.Json)
        }
        return JsonPath.parse(response.bodyAsText())
    }
}