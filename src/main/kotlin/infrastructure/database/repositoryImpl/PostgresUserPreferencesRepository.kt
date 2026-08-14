package com.tenmilelabs.infrastructure.database.repositoryImpl

import com.tenmilelabs.domain.repository.UserPreferencesRepository
import com.tenmilelabs.infrastructure.database.mappers.suspendTransaction
import com.tenmilelabs.infrastructure.database.tables.UserPreferencesTable
import com.tenmilelabs.infrastructure.database.tables.UserTable
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

class PostgresUserPreferencesRepository : UserPreferencesRepository {
    override suspend fun getUserPreferences(userId: UUID): String? = suspendTransaction {
        UserPreferencesTable
            .selectAll()
            .where { UserPreferencesTable.user_id eq EntityID(userId, UserTable) }
            .firstOrNull()
            ?.get(UserPreferencesTable.preferences)
    }

    override suspend fun upsertUserPreferences(
        userId: UUID,
        preferencesJson: String,
        updatedAt: Instant
    ): Unit = suspendTransaction {
        val existing = UserPreferencesTable
            .selectAll()
            .where { UserPreferencesTable.user_id eq EntityID(userId, UserTable) }
            .limit(1)
            .any()

        if (existing) {
            UserPreferencesTable.update({ UserPreferencesTable.user_id eq EntityID(userId, UserTable) }) {
                it[preferences] = preferencesJson
                it[updated_at] = updatedAt
            }
        } else {
            UserPreferencesTable.insert {
                it[user_id] = EntityID(userId, UserTable)
                it[preferences] = preferencesJson
                it[updated_at] = updatedAt
            }
        }
    }
}
