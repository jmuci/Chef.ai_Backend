package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.domain.service.RecipeSearchService
import com.tenmilelabs.infrastructure.auth.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.util.UUID

val RECIPE_SEARCH_RATE_LIMIT_NAME = RateLimitName("recipe-search")

fun Route.recipeSearchRoutes(recipeSearchService: RecipeSearchService) {
    rateLimit(RECIPE_SEARCH_RATE_LIMIT_NAME) {
        get("/api/v1/recipes/search") {
            // Null for an anonymous caller - this route is mounted under
            // authenticate("auth-jwt", optional = true), see Routing.kt. Null is not an error
            // here; it scopes the search to PUBLIC recipes only.
            val userId: UUID? = parseUuidOrNull(call.userId)

            val query = call.request.queryParameters["q"]?.trim()
            if (query.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("q query parameter is required"))
                return@get
            }

            val limit = call.request.queryParameters["limit"]?.toIntOrNull()
            val offset = call.request.queryParameters["offset"]?.toIntOrNull()

            try {
                val response = recipeSearchService.search(userId, query, limit, offset)
                call.respond(HttpStatusCode.OK, response)
            } catch (ex: Exception) {
                call.application.environment.log.error("Recipe search failed for query='$query'", ex)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Recipe search failed"))
            }
        }
    }
}

// RecipeImageRoutes.kt already declares a parseUuidOrNull, but Kotlin `private` top-level
// functions are file-scoped, not package-scoped — it isn't visible here. SyncRoutes.kt's
// parseUserId is the same duplication for the same reason; this is the established idiom.
private fun parseUuidOrNull(value: String?): UUID? =
    try {
        value?.let(UUID::fromString)
    } catch (_: IllegalArgumentException) {
        null
    }
