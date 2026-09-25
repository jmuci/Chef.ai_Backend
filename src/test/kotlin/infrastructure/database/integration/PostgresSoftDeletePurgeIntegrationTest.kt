package com.tenmilelabs.infrastructure.database.integration

import com.tenmilelabs.infrastructure.database.initDatabaseAndSchema
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresRecipesRepository
import com.tenmilelabs.infrastructure.database.tables.RecipeStepTable
import com.tenmilelabs.infrastructure.database.tables.RecipeTable
import com.tenmilelabs.infrastructure.database.tables.UserTable
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("db-integration")
class PostgresSoftDeletePurgeIntegrationTest {

    @BeforeEach
    fun resetSchema() {
        Database.connect(
            // chefai_test, deliberately NOT the docker-compose dev database: resetSchema() drops
            // the public schema. CI sets DB_URL.
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

    /**
     * The Exposed-provisioned schema has no ON DELETE CASCADE on recipe children, and a sync-pushed
     * tombstone keeps its children — the purge used to hit a foreign-key violation on exactly these
     * rows. Retention also runs off server_updated_at, not the client-supplied deleted_at.
     */
    @Test
    fun purgeDeletesChildrenAndMeasuresRetentionFromTheServerTimestamp() = runBlocking {
        val repo = PostgresRecipesRepository(KtorSimpleLogger("purge-test"))
        val ownerId = insertUser()
        // Recorded by the server long ago, carrying a recent client deleted_at.
        val old = insertTombstoneWithStep(ownerId, clientDeletedAt = 90_000L, serverUpdatedAt = 1_000L)
        // Recorded by the server recently, carrying an ancient (skewed) client deleted_at.
        val fresh = insertTombstoneWithStep(ownerId, clientDeletedAt = 1L, serverUpdatedAt = 100_000L)

        val purged = repo.purgeSoftDeletedRecipes(olderThanMillis = 50_000L, limit = 10)

        assertEquals(1, purged)
        assertFalse(recipeExists(old))
        assertTrue(recipeExists(fresh))
    }

    private fun recipeExists(id: UUID): Boolean = transaction {
        RecipeTable.selectAll().where { RecipeTable.id eq id }.any()
    }

    private fun insertUser(): UUID {
        val id = UUID.randomUUID()
        transaction {
            UserTable.insert {
                it[UserTable.id] = EntityID(id, UserTable)
                it[user_name] = "u"
                it[email] = "u-$id@example.com"
                it[display_name] = "U"
                it[avatar_url] = ""
                it[password_hash] = "hash"
            }
        }
        return id
    }

    private fun insertTombstoneWithStep(ownerId: UUID, clientDeletedAt: Long, serverUpdatedAt: Long): UUID {
        val recipeId = UUID.randomUUID()
        val serverInstant = Instant.fromEpochMilliseconds(serverUpdatedAt)
        transaction {
            RecipeTable.insert {
                it[id] = EntityID(recipeId, RecipeTable)
                it[title] = "t"
                it[description] = "d"
                it[image_url] = ""
                it[image_url_thumbnail] = ""
                it[prep_time_minutes] = 1
                it[cook_time_minutes] = 1
                it[servings] = 1
                it[creator_id] = EntityID(ownerId, UserTable)
                it[recipe_external_url] = null
                it[privacy] = "PRIVATE"
                it[updated_at] = clientDeletedAt
                it[deleted_at] = clientDeletedAt
                it[server_updated_at] = serverInstant
            }
            RecipeStepTable.insert {
                it[id] = EntityID(UUID.randomUUID(), RecipeStepTable)
                it[recipe_id] = EntityID(recipeId, RecipeTable)
                it[order_index] = 0
                it[instruction] = "step"
                it[updated_at] = clientDeletedAt
                it[deleted_at] = clientDeletedAt
                it[server_updated_at] = serverInstant
            }
        }
        return recipeId
    }
}
