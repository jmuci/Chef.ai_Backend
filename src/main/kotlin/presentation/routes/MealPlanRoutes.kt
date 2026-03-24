package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.domain.service.MealPlanGenerationService
import com.tenmilelabs.infrastructure.auth.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

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
