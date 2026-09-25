package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.application.dto.UserPreferencesResponse
import com.tenmilelabs.domain.repository.UserPreferencesRepository
import com.tenmilelabs.domain.service.MealPlanGenerationService
import com.tenmilelabs.domain.service.MealPlanPreferences
import com.tenmilelabs.infrastructure.auth.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.util.UUID

fun Route.userPreferencesRoutes(
    userPreferencesRepository: UserPreferencesRepository,
    mealPlanGenerationService: MealPlanGenerationService
) {
    route("/user/preferences") {
        get {
            val userId = call.userId?.let {
                try { UUID.fromString(it) } catch (_: IllegalArgumentException) { null }
            } ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("User not authenticated"))
                return@get
            }

            val storedJson = userPreferencesRepository.getUserPreferences(userId) ?: run {
                call.respond(HttpStatusCode.NoContent)
                return@get
            }

            // parsePreferences tolerates wrong-typed fields, but a stored blob that isn't a JSON
            // object at all (it's saved verbatim from pushes) still throws. Answer as "nothing
            // usable stored" rather than 500 on every read until the next push overwrites it.
            val prefs = try {
                mealPlanGenerationService.parsePreferences(storedJson)
            } catch (ex: IllegalArgumentException) {
                // SerializationException (invalid JSON) is an IllegalArgumentException subtype, as
                // is the one `.jsonObject` throws for a non-object.
                call.application.environment.log.warn("Stored preferences for user $userId are unparseable", ex)
                call.respond(HttpStatusCode.NoContent)
                return@get
            }
            call.respond(HttpStatusCode.OK, prefs.toResponse())
        }
    }
}

private fun MealPlanPreferences.toResponse() = UserPreferencesResponse(
    planLengthDays = planLengthDays,
    mealType = mealType.name,
    dietaryRestrictions = dietaryRestrictions,
    recipeSource = recipeSource,
    maxPrepTimeMinutes = maxPrepTimeMinutes,
    servingsPerMeal = servingsPerMeal,
    batchCooking = batchCooking,
    leftoverFriendly = leftoverFriendly,
    varietyPreference = varietyPreference.name
)
