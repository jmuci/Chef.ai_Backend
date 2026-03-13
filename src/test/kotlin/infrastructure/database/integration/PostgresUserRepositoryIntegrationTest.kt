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

        val byEmail = repo.findUserByEmail(email)
        assertNotNull(byEmail)
        assertEquals(created.uuid, byEmail.uuid)

        val byId = repo.findUserById(created.uuid)
        assertNotNull(byId)
        assertEquals(email, byId.email)
    }
}
