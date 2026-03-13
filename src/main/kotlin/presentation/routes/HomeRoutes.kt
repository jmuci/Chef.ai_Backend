package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.domain.service.HomeLayoutService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.homeRoutes(homeLayoutService: HomeLayoutService) {
    get("/api/v1/home/layout") {
        try {
            val layout = homeLayoutService.getHomeLayout()
            val shouldReturnNotModified = call.request.headers[HttpHeaders.IfNoneMatch]
                ?.matchesChecksum(layout.layoutChecksum)
                ?: false

            if (shouldReturnNotModified) {
                call.response.status(HttpStatusCode.NotModified)
                return@get
            }

            call.response.headers.append(HttpHeaders.ETag, layout.layoutChecksum.asEtag())
            call.response.headers.append(HttpHeaders.CacheControl, HomeLayoutService.CACHE_CONTROL_VALUE)
            call.response.headers.append(HomeLayoutService.MIN_SCHEMA_VERSION_HEADER, HomeLayoutService.MIN_SCHEMA_VERSION)
            call.respond(HttpStatusCode.OK, layout)
        } catch (ex: Exception) {
            call.application.environment.log.error("Failed to resolve home layout", ex)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to load home layout"))
        }
    }
}

private fun String.asEtag(): String = "\"$this\""

private fun String.matchesChecksum(currentChecksum: String): Boolean {
    val candidateTags = split(",").map { it.trim() }
    if (candidateTags.any { it == "*" }) {
        return true
    }

    return candidateTags
        .map {
            it.removePrefix("W/")
                .removePrefix("w/")
                .trim()
                .removeSurrounding("\"")
        }
        .any { it == currentChecksum }
}
