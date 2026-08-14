package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.domain.repository.UserPreferencesRepository
import kotlinx.datetime.Instant
import java.util.UUID

class FakeUserPreferencesRepository : UserPreferencesRepository {
    private val preferences = mutableMapOf<UUID, String>()

    override suspend fun getUserPreferences(userId: UUID): String? = preferences[userId]

    override suspend fun upsertUserPreferences(userId: UUID, preferencesJson: String, updatedAt: Instant) {
        preferences[userId] = preferencesJson
    }
}
