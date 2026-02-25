package com.tenmilelabs.domain.service

import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.domain.model.Recipe
import com.tenmilelabs.domain.repository.RecipesRepository
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SoftDeletePurgeServiceTest {

    @Test
    fun purgeOnceUses90DayCutoffAndBatchLimit() = runTest {
        val repo = RecordingRecipesRepository(deletedCount = 7)
        val service = SoftDeletePurgeService(
            recipesRepository = repo,
            log = KtorSimpleLogger("SoftDeletePurgeServiceTest"),
            clock = fixedClock(Instant.fromEpochMilliseconds(1_000_000_000L))
        )

        val result = service.purgeOnce(
            SoftDeletePurgeConfig(
                enabled = true,
                retentionDays = 90,
                intervalHours = 24,
                batchSize = 250,
                dryRun = false
            )
        )

        assertEquals(7, result)
        assertEquals(250, repo.lastLimit)
        assertEquals(1_000_000_000L - 7_776_000_000L, repo.lastOlderThanMillis)
    }

    @Test
    fun purgeOnceSkipsDeleteInDryRunMode() = runTest {
        val repo = RecordingRecipesRepository(deletedCount = 7)
        val service = SoftDeletePurgeService(
            recipesRepository = repo,
            log = KtorSimpleLogger("SoftDeletePurgeServiceTest"),
            clock = fixedClock(Instant.fromEpochMilliseconds(1_000_000_000L))
        )

        val result = service.purgeOnce(
            SoftDeletePurgeConfig(
                enabled = true,
                retentionDays = 90,
                intervalHours = 24,
                batchSize = 250,
                dryRun = true
            )
        )

        assertEquals(0, result)
        assertNull(repo.lastOlderThanMillis)
    }

    @Test
    fun purgeOnceSkipsDeleteForInvalidConfig() = runTest {
        val repo = RecordingRecipesRepository(deletedCount = 7)
        val service = SoftDeletePurgeService(
            recipesRepository = repo,
            log = KtorSimpleLogger("SoftDeletePurgeServiceTest")
        )

        val result = service.purgeOnce(
            SoftDeletePurgeConfig(
                enabled = true,
                retentionDays = 0,
                intervalHours = 24,
                batchSize = 250,
                dryRun = false
            )
        )

        assertEquals(0, result)
        assertNull(repo.lastOlderThanMillis)
    }

    private fun fixedClock(instant: Instant): Clock = object : Clock {
        override fun now(): Instant = instant
    }
}

private class RecordingRecipesRepository(
    private val deletedCount: Int
) : RecipesRepository {
    var lastOlderThanMillis: Long? = null
    var lastLimit: Int? = null

    override suspend fun allRecipes(): List<Recipe> = emptyList()
    override suspend fun recipesByUserId(userId: UUID): List<Recipe> = emptyList()
    override suspend fun publicRecipes(): List<Recipe> = emptyList()
    override suspend fun recipeByTitle(title: String): Recipe? = null
    override suspend fun recipeById(id: String): Recipe? = null
    override suspend fun recipeByIdAndUserId(id: String, userId: UUID): Recipe? = null
    override suspend fun addRecipe(recipeRequest: CreateRecipeRequest, userId: UUID): String = "unused"
    override suspend fun removeRecipe(uuid: String, userId: UUID): Boolean = false

    override suspend fun purgeSoftDeletedRecipes(olderThanMillis: Long, limit: Int): Int {
        lastOlderThanMillis = olderThanMillis
        lastLimit = limit
        return deletedCount
    }
}

