package com.tenmilelabs.domain.repository

import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Stores the user's most recently used meal-plan generation preferences, so the wizard can
 * pre-fill instead of starting blank. One row per user; kept in sync automatically whenever a
 * meal plan is pushed (see [com.tenmilelabs.domain.service.SyncService.processMealPlans]) rather
 * than through an explicit "save defaults" action.
 */
interface UserPreferencesRepository {
    /**
     * Returns the raw `preferencesJson` blob last stored for [userId], or null if the user has
     * never pushed a meal plan. Parsing/defaulting is the caller's responsibility (see
     * [com.tenmilelabs.domain.service.MealPlanGenerationService.parsePreferences]).
     */
    suspend fun getUserPreferences(userId: UUID): String?

    /**
     * Replaces the stored preferences for [userId] with [preferencesJson]. Upsert semantics —
     * safe to call whether or not a row already exists.
     */
    suspend fun upsertUserPreferences(userId: UUID, preferencesJson: String, updatedAt: Instant)
}
