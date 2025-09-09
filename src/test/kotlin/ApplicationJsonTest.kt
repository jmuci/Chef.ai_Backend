package com.tenmilelabs

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.tenmilelabs.domain.model.Label
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationJsonPathTest {
    @Test
    fun recipesCanBeFound() = testApplication {
        application {
            module()
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
            module()
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