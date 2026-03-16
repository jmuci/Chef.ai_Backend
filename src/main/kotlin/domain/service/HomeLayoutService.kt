package com.tenmilelabs.domain.service

import com.tenmilelabs.application.dto.*
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.MessageDigest

class HomeLayoutService(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
        encodeDefaults = false
    },
    private val loadLayoutJson: () -> String = { loadLayoutFromResources(DEFAULT_LAYOUT_RESOURCE_PATH) },
    private val loadSidecarJson: () -> String = { loadLayoutFromResources(DEFAULT_SIDECAR_RESOURCE_PATH) },
    val log: Logger = KtorSimpleLogger("HomeLayoutService"),
) {

    /**
     * Builds the server-driven Home payload returned by `/api/v1/home/layout`.
     *
     * Current MVP flow:
     * - Loads a static JSON layout from the bundled resource file (`home_layout.json`).
     * - Decodes that JSON into typed `HomeComponent` models.
     * - Sanitizes unknown/invalid components so malformed config does not crash the endpoint.
     * - Serializes sanitized components into canonical JSON and computes MD5 checksum.
     * - Returns `HomeLayoutResponse` with that checksum for ETag/If-None-Match revalidation.
     *
     * Future personalization flow:
     * - Keep this endpoint and response schema stable.
     * - Use `userId` to select or generate user-specific component sets (preferences/history/A-B cohorts).
     * - Keep checksum generation identical so client caching behavior remains unchanged.
     * - Load home layout configuration from a database or other persistence layer.
     */
    fun getHomeLayout(_userId: String? = null): HomeLayoutResponse {
        val rawJson = loadLayoutJson()
        val resource = json.decodeFromString<HomeLayoutResource>(rawJson)
        // Unknown/unexpected components are dropped so a bad resource entry cannot break the endpoint.
        val cleanedComponents = sanitizeComponents(resource.components)
        // Checksum is computed from the canonical serialized components payload used for ETag revalidation.
        val canonicalComponentsJson = json.encodeToString(cleanedComponents)
        val checksum = computeLayoutChecksum(canonicalComponentsJson)

        val sidecar = json.decodeFromString<HomeSidecar>(loadSidecarJson())

        log.info("HomeLayoutService: Serving home layout with checksum $checksum, v ${resource.schemaVersion} and ${cleanedComponents.size} components, ${sidecar.recipes.size} sidecar recipes.")
        return HomeLayoutResponse(
            schemaVersion = resource.schemaVersion,
            layoutChecksum = checksum,
            components = cleanedComponents,
            sidecar = sidecar,
        )
    }

    private fun sanitizeComponents(components: List<HomeComponent>): List<HomeComponent> =
        components.mapNotNull(::sanitizeComponent)

    private fun sanitizeComponent(component: HomeComponent): HomeComponent? =
        when (component) {
            is UnknownHomeComponent -> null
            is CarouselComponent -> component.copy(items = sanitizeCarouselItems(component.items))
            is SectionHeaderComponent -> component
            is LargeCardComponent -> component
            is SquaredCardComponent -> component
            is ListCardComponent -> component
        }

    private fun sanitizeCarouselItems(items: List<HomeComponent>): List<HomeComponent> =
        items.mapNotNull { item ->
            when (val cleaned = sanitizeComponent(item)) {
                // Carousel supports only card item components in this schema version.
                is LargeCardComponent -> cleaned
                is SquaredCardComponent -> cleaned
                is ListCardComponent -> cleaned
                else -> null
            }
        }

    companion object {
        const val DEFAULT_SCHEMA_VERSION = "1.0.0"
        const val MIN_SCHEMA_VERSION = "1.0.0"
        const val MIN_SCHEMA_VERSION_HEADER = "X-Min-Schema-Version"
        const val CACHE_CONTROL_VALUE = "max-age=300"
        const val DEFAULT_LAYOUT_RESOURCE_PATH = "home_layout.json"
        const val DEFAULT_SIDECAR_RESOURCE_PATH = "home_sidecar.json"

        /** MD5 is used only for equality checks (If-None-Match), not for security. */
        fun computeLayoutChecksum(componentsJson: String): String {
            val digest = MessageDigest.getInstance("MD5")
            val hashBytes = digest.digest(componentsJson.toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }

        private fun loadLayoutFromResources(path: String): String {
            // Resource-backed layout lets us update Home SDUI without changing Kotlin source.
            val stream = object {}.javaClass.classLoader.getResourceAsStream(path)
                ?: throw IOException("Home layout resource '$path' was not found")
            return stream.bufferedReader().use { it.readText() }
        }
    }
}

@Serializable
private data class HomeLayoutResource(
    val schemaVersion: String = HomeLayoutService.DEFAULT_SCHEMA_VERSION,
    val layoutChecksum: String? = null,
    val components: List<HomeComponent>,
)
