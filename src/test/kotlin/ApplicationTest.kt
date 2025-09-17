package com.tenmilelabs

import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.application.service.module
import com.tenmilelabs.infrastructure.database.FakeRecipesRepository
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository)
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertContains(bodyAsText(), "Coming Soon")

        }
    }

    @Test
    fun recipesCanBeFoundByLabel() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository)
        }

        val response = client.get("/recipes/byLabel?label=Vegetarian")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(body, "Recipe 3")
        assertContains(body, "Description 3")
    }

    @Test
    fun recipesCanBeFoundByName() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository)
        }

        val response = client.get("/recipes/byName?title=Recipe+1") {
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
            module(recipeRepository = FakeRecipesRepository)
        }

        val response = client.get("/recipes/byId?uuid=1") {
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
            module(recipeRepository = FakeRecipesRepository)
        }

        val response = client.delete("/recipes?uuid=6") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.NoContent, response.status)

        val response2 = client.get("/recipes/byId?uuid=6") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.NotFound, response2.status)
    }

    @Test
    fun invalidLabelProduces400() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository)
        }

        val response = client.get("/recipes/byLabel?label=FooBAr")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun unusedLabelProduces404() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository)
        }

        val response = client.get("/recipes/byLabel/Pescatarian")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }


    @Test
    fun newRecipesCanBeAdded() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val response1 = client.post("/recipes") {
            header(
                HttpHeaders.ContentType,
                ContentType.Application.Json
            )
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(
                CreateRecipeRequest(
                    "swimming peaches",
                    "LowCarb",
                    "Go to the beach",
                    "45",
                    "https://example.com",
                    "https://example.com"
                )

            )
        }

        assertEquals(HttpStatusCode.Created, response1.status)

        val response2 = client.get("/recipes")
        assertEquals(HttpStatusCode.OK, response2.status)
        val body = response2.bodyAsText()

        assertContains(body, "swimming peaches")
        assertContains(body, "Go to the beach")
    }

    @Test
    fun postCreateRecipeRequest_withWrongLabel() = testApplication {
        application {
            module(recipeRepository = FakeRecipesRepository)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val response1 = client.post("/recipes") {
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
            module(recipeRepository = FakeRecipesRepository)
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
                    "prepTimeMins" to "45",
                    "recipeUrl" to "https://example.com",
                    "imageUrl" to "https://example.com"
                ).formUrlEncode()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response1.status)

    }

}
