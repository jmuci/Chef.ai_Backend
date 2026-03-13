package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.HomeLayoutResponse
import com.tenmilelabs.domain.service.HomeLayoutService
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HomeRoutesIntegrationTest {

    @Test
    fun `home layout endpoint accepts anonymous requests and returns expected headers`() = testApplication {
        application { configureHomeRouteOnly() }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val response = client.get("/api/v1/home/layout") {
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<HomeLayoutResponse>()
        assertEquals("1.0.0", body.schemaVersion)
        assertTrue(body.components.isNotEmpty())

        val etag = response.headers[HttpHeaders.ETag]
        assertNotNull(etag)
        assertEquals("\"${body.layoutChecksum}\"", etag)
        assertEquals("max-age=300", response.headers[HttpHeaders.CacheControl])
        assertEquals("1.0.0", response.headers["X-Min-Schema-Version"])
    }

    @Test
    fun `if-none-match with current checksum returns 304 without body`() = testApplication {
        application { configureHomeRouteOnly() }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val initialResponse = client.get("/api/v1/home/layout")
        assertEquals(HttpStatusCode.OK, initialResponse.status)
        val checksum = initialResponse.body<HomeLayoutResponse>().layoutChecksum

        val cachedResponse = client.get("/api/v1/home/layout") {
            header(HttpHeaders.IfNoneMatch, "\"$checksum\"")
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.NotModified, cachedResponse.status)
        assertEquals("", cachedResponse.bodyAsText())
    }

    @Test
    fun `if-none-match with stale checksum returns 200 and full layout`() = testApplication {
        application { configureHomeRouteOnly() }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val response = client.get("/api/v1/home/layout") {
            header(HttpHeaders.IfNoneMatch, "\"stale-checksum\"")
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<HomeLayoutResponse>()
        assertTrue(body.layoutChecksum.isNotBlank())
        assertTrue(body.components.isNotEmpty())
    }

    @Test
    fun `response components match bundled home layout resource structure`() = testApplication {
        application { configureHomeRouteOnly() }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val response = client.get("/api/v1/home/layout") {
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val responseJson = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val resourceJson = json.parseToJsonElement(loadResource("home_layout.json")).jsonObject

        assertEquals(resourceJson["schemaVersion"], responseJson["schemaVersion"])
        assertEquals(resourceJson["components"], responseJson["components"])
        assertTrue(responseJson["layoutChecksum"]?.jsonPrimitive?.content?.isNotBlank() == true)
    }

    private fun loadResource(path: String): String {
        val stream = this::class.java.classLoader.getResourceAsStream(path)
            ?: error("Resource '$path' not found")
        return stream.bufferedReader().use { it.readText() }
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = true
        }
    }
}

private fun io.ktor.server.application.Application.configureHomeRouteOnly() {
    install(ServerContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = true
                encodeDefaults = false
            }
        )
    }
    routing {
        homeRoutes(HomeLayoutService())
    }
}
