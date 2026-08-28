package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.application.dto.GenerateMealPlanStatelessRequest
import com.tenmilelabs.application.dto.GenerateMealPlanStatelessResponse
import com.tenmilelabs.domain.service.MealPlanGenerationService
import com.tenmilelabs.domain.service.SyncService
import com.tenmilelabs.infrastructure.auth.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

val MEAL_PLAN_GENERATE_RATE_LIMIT_NAME = RateLimitName("meal-plan-generate")

/**
 * `POST /api/v1/meal-plans/generate` — the anonymous-capable, stateless counterpart to
 * [mealPlanRoutes]'s `/meal-plans/{mealPlanId}/generate`. No plan id, no persistence: it runs
 * [MealPlanGenerationService.generateStateless] synchronously and returns the assigned days plus
 * every recipe aggregate they reference, shaped like a pull page so the caller can reuse its
 * existing aggregate-upsert code in one round trip (see [GenerateMealPlanStatelessResponse]).
 *
 * Mounted alongside [recipeSearchRoutes] / [recipeDetailRoutes] under
 * `authenticate("auth-jwt", optional = true)` in Routing.kt — this is the endpoint that closes the
 * 16-vs-789-candidate gap for an anonymous device generating an INCLUDE_PUBLIC plan.
 */
fun Route.mealPlanGenerationRoutes(
    mealPlanGenerationService: MealPlanGenerationService,
    syncService: SyncService
) {
    rateLimit(MEAL_PLAN_GENERATE_RATE_LIMIT_NAME) {
        post("/api/v1/meal-plans/generate") {
            // Null is not an error here - it's an anonymous caller, same as recipeSearchRoutes.
            val userId: UUID? = call.userId?.let {
                try { UUID.fromString(it) } catch (_: IllegalArgumentException) { null }
            }

            val request = try {
                call.receive<GenerateMealPlanStatelessRequest>()
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("preferencesJson is required"))
                return@post
            }

            if (request.preferencesJson.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("preferencesJson must not be blank"))
                return@post
            }

            try {
                // parsePreferences is lenient about missing/wrong-typed fields (defaults fill in),
                // but still throws on a preferencesJson value that isn't valid JSON at all - check
                // that up front so a malformed value 400s here rather than falling through to the
                // generic 500 below.
                mealPlanGenerationService.parsePreferences(request.preferencesJson)
            } catch (ex: Exception) {
                call.application.environment.log.warn("Meal plan generation preferencesJson parse failed: ${ex.message}")
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("preferencesJson is not valid JSON"))
                return@post
            }

            try {
                val days = mealPlanGenerationService.generateStateless(userId, request.preferencesJson)

                val recipeIds = days
                    .flatMap { listOfNotNull(it.dinnerRecipeId, it.lunchRecipeId) }
                    .map { UUID.fromString(it) }
                    .toSet()
                val bundle = syncService.getRecipeDetails(userId, recipeIds)

                call.respond(
                    HttpStatusCode.OK,
                    GenerateMealPlanStatelessResponse(
                        days = days,
                        recipes = bundle.recipes,
                        referenceData = bundle.referenceData,
                        creators = bundle.creators
                    )
                )
            } catch (ex: Exception) {
                call.application.environment.log.error("Stateless meal plan generation failed for userId=$userId", ex)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Meal plan generation failed"))
            }
        }
    }
}

fun Route.mealPlanRoutes(mealPlanGenerationService: MealPlanGenerationService) {
    route("/meal-plans") {
        post("/{mealPlanId}/generate") {
            val userId = call.userId?.let {
                try { UUID.fromString(it) } catch (_: IllegalArgumentException) { null }
            } ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("User not authenticated"))
                return@post
            }

            val mealPlanId = call.parameters["mealPlanId"]?.let {
                try {
                    UUID.fromString(it)
                } catch (_: IllegalArgumentException) {
                    null
                }
            } ?: run {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("mealPlanId path parameter is not a valid UUID")
                )
                return@post
            }

            val response = mealPlanGenerationService.startGeneration(mealPlanId, userId) ?: run {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Meal plan not found or not owned by caller")
                )
                return@post
            }

            call.respond(HttpStatusCode.Accepted, response)
        }
    }
}
