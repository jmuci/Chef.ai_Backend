package com.tenmilelabs.infrastructure.database.integration

import com.tenmilelabs.infrastructure.database.initDatabaseAndSchema
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresUserRepository
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Tag("db-integration")
class PostgresUserRepositoryIntegrationTest {

    private val log = KtorSimpleLogger("PostgresUserRepositoryIntegrationTest")

    @BeforeEach
    fun resetSchema() {
        Database.connect(
            // chefai_test, deliberately NOT the docker-compose dev database: resetSchema()
            // drops the public schema, which silently wiped a seeded chefai_db. CI sets DB_URL.
            url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/chefai_test",
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
    fun createAndReadUserRoundTrip() = runBlocking {
        val repo = PostgresUserRepository(log)
        val email = "user-${UUID.randomUUID()}@example.com"

        val created = repo.createUser(
            email = email,
            username = "db-user",
            passwordHash = "hashed-password"
        )
        assertNotNull(created)
        assertEquals(email, created.email)
        assertEquals("db-user", created.username)

        val byEmail = repo.findUserByEmail(email)
        assertNotNull(byEmail)
        assertEquals(created.uuid, byEmail.uuid)

        val byId = repo.findUserById(created.uuid)
        assertNotNull(byId)
        assertEquals(email, byId.email)
        assertEquals("db-user", byId.username)
    }

    /**
     * Regression test for a bug where `daoToUser()` read `username` from the DAO's
     * `displayName` column instead of its `username` column. `display_name` defaults to `""` at
     * the DB level and `createUser()` never sets it, so every `User.username` — including
     * `/auth/register`'s response field — came back blank in production. Unit tests against
     * `FakeUserRepository` never caught this because the fake bypasses `daoToUser` entirely and
     * stores the real username directly; only a real Postgres round-trip through the actual DAO
     * mapping exercises the bug.
     */
    @Test
    fun createdUserUsernameIsNotSilentlyBlank() = runBlocking {
        val repo = PostgresUserRepository(log)
        val email = "user-${UUID.randomUUID()}@example.com"

        val created = repo.createUser(
            email = email,
            username = "alice",
            passwordHash = "hashed-password"
        )

        assertNotNull(created)
        assertEquals("alice", created.username)
        assertNotNull(repo.findUserById(created.uuid))
        assertEquals("alice", repo.findUserById(created.uuid)!!.username)
    }
}
