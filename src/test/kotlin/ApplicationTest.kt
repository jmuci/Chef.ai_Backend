package com.tenmilelabs

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        application {
            module()
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertContains(bodyAsText(), "Coming Soon")

        }
    }
    @Test
    fun recipesCanBeFoundByLabel() = testApplication {
        application {
            module()
        }

        val response = client.get("/recipes/byLabel?label=Vegetarian")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(body, "Recipe 3")
        assertContains(body, "Description 3")
    }

    @Test
    fun invalidLabelProduces400() = testApplication {
        application {
            module()
        }

        val response = client.get("/recipes/byLabel?label=FooBAr")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun unusedLabelProduces404() = testApplication {
        application {
            module()
        }

        val response = client.get("/recipes/byLabel/Pescatarian")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun newRecipesCanBeAdded() = testApplication {
        application {
            module()
        }

        val response1 = client.post("/recipes") {
            header(
                HttpHeaders.ContentType,
                ContentType.Application.FormUrlEncoded.toString()
            )
            setBody(
                listOf(
                    "title" to "swimming peaches",
                    "description" to "Go to the beach",
                    "label" to "LowCarb",
                    "preparationTimeMinutes" to "45",
                    "recipeUrl" to "https://example.com",
                    "imageUrl" to "https://example.com"
                ).formUrlEncode()
            )
        }

        assertEquals(HttpStatusCode.Created, response1.status)

        val response2 = client.get("/recipes")
        assertEquals(HttpStatusCode.OK, response2.status)
        val body = response2.bodyAsText()

        assertContains(body, "swimming")
        assertContains(body, "Go to the beach")
    }

    @Test
    fun newRecipesFailToBeAddedWithInvalidLabel() = testApplication {
        application {
            module()
        }

        val response1 = client.post("/recipes") {
            header(
                HttpHeaders.ContentType,
                ContentType.Application.FormUrlEncoded.toString()
            )
            setBody(
                listOf(
                    "title" to "swimming peaches",
                    "description" to "Go to the beach",
                    "label" to "Invalid",
                    "preparationTimeMinutes" to "45",
                    "recipeUrl" to "https://example.com",
                    "imageUrl" to "https://example.com"
                ).formUrlEncode()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response1.status)

    }
}
