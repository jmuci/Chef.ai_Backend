package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.application.dto.RecipeDetailResponse
import com.tenmilelabs.domain.service.RecipeDetailResult
import com.tenmilelabs.domain.service.SyncService
import com.tenmilelabs.infrastructure.auth.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.util.UUID

val RECIPE_DETAIL_RATE_LIMIT_NAME = RateLimitName("recipe-detail")

/**
 * `GET /api/v1/recipes/{recipeId}` — anonymous-capable single-recipe fetch, see
 * [SyncService.getRecipeDetail]. Mounted alongside [recipeSearchRoutes] under
 * `authenticate("auth-jwt", optional = true)` in Routing.kt: it's the endpoint a search
 * result's tap-through calls when the recipe isn't in the client's local DB yet
 * (ChefAI#186). A dedicated rate-limit bucket, not [RECIPE_SEARCH_RATE_LIMIT_NAME], so
 * exhausting one doesn't block the other — a heavy searcher shouldn't lose the ability to
 * open what they found.
 */
fun Route.recipeDetailRoutes(syncService: SyncService) {
    rateLimit(RECIPE_DETAIL_RATE_LIMIT_NAME) {
        get("/api/v1/recipes/{recipeId}") {
            // Null for an anonymous caller, same as recipeSearchRoutes - this route shares its
            // optional-auth mount point in Routing.kt.
            val userId: UUID? = parseUuidOrNull(call.userId)

            val recipeId = call.parameters["recipeId"]?.let(::parseUuidOrNull)
            if (recipeId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("recipeId is not a valid UUID"))
                return@get
            }

            try {
                when (val result = syncService.getRecipeDetail(userId, recipeId)) {
                    is RecipeDetailResult.Found ->
                        call.respond(
                            HttpStatusCode.OK,
                            RecipeDetailResponse(result.recipe, result.referenceData, result.creators)
                        )
                    RecipeDetailResult.NotFound ->
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Recipe not found"))
                }
            } catch (ex: Exception) {
                call.application.environment.log.error("Recipe detail fetch failed for recipeId=$recipeId", ex)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Recipe detail fetch failed"))
            }
        }
    }
}

// RecipeSearchRoutes.kt already declares an identical parseUuidOrNull, but Kotlin `private`
// top-level functions are file-scoped, not package-scoped - it isn't visible here. That
// file's own comment documents this as the established idiom, not an oversight.
private fun parseUuidOrNull(value: String?): UUID? =
    try {
        value?.let(UUID::fromString)
    } catch (_: IllegalArgumentException) {
        null
    }
