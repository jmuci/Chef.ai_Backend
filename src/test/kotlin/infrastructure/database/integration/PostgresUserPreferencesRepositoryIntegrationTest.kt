package com.tenmilelabs.infrastructure.database.integration

import com.tenmilelabs.infrastructure.database.initDatabaseAndSchema
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresUserPreferencesRepository
import com.tenmilelabs.infrastructure.database.tables.UserTable
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Tag("db-integration")
class PostgresUserPreferencesRepositoryIntegrationTest {

    @BeforeEach
    fun resetSchema() {
        Database.connect(
            url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/chefai_db",
            user = System.getenv("DB_USER") ?: "postgres",
            password = System.getenv("DB_PASSWORD") ?: "password"
        )

        transaction {
            exec("DROP SCHEMA IF EXISTS public CASCADE")
            exec("CREATE SCHEMA public")
        }

        initDatabaseAndSchema()
    }

    @Test
    fun getUserPreferencesReturnsNullBeforeAnyUpsert() = runBlocking {
        val repo = PostgresUserPreferencesRepository()
        val userId = seedUser()

        assertNull(repo.getUserPreferences(userId))
    }

    @Test
    fun upsertUserPreferencesThenOverwritesRatherThanDuplicating() = runBlocking {
        val repo = PostgresUserPreferencesRepository()
        val userId = seedUser()

        val firstJson = """{"planLengthDays":3,"mealType":"DINNER","recipeSource":"INCLUDE_PUBLIC"}"""
        repo.upsertUserPreferences(userId, firstJson, Instant.fromEpochMilliseconds(1_000L))
        assertEquals(firstJson, repo.getUserPreferences(userId))

        val secondJson = """{"planLengthDays":7,"mealType":"DINNER_AND_LUNCH","recipeSource":"COLLECTION_ONLY"}"""
        repo.upsertUserPreferences(userId, secondJson, Instant.fromEpochMilliseconds(2_000L))

        assertEquals(secondJson, repo.getUserPreferences(userId))
    }

    private fun seedUser(): UUID {
        val userId = UUID.randomUUID()
        transaction {
            UserTable.insert {
                it[UserTable.id] = EntityID(userId, UserTable)
                it[user_name] = "user"
                it[email] = "user-${userId}@example.com"
                it[display_name] = "User"
                it[avatar_url] = ""
                it[password_hash] = "hash"
            }
        }
        return userId
    }
}
