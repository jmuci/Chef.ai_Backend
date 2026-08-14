package com.tenmilelabs.application.dto

import kotlinx.serialization.Serializable

/**
 * The user's most recently used meal-plan generation preferences (see
 * `GET /user/preferences`). Same field shape as the `preferencesJson` blob pushed with a meal
 * plan — mirrors [com.tenmilelabs.domain.service.MealPlanPreferences] in wire format.
 */
@Serializable
data class UserPreferencesResponse(
    val planLengthDays: Int,
    val mealType: String,
    val dietaryRestrictions: List<String>,
    val recipeSource: String,
    val maxPrepTimeMinutes: Int?,
    val servingsPerMeal: Int,
    val batchCooking: Boolean,
    val leftoverFriendly: Boolean,
    val varietyPreference: String
)
