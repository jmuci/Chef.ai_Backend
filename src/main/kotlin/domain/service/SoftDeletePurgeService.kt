package com.tenmilelabs.domain.service

import com.tenmilelabs.domain.repository.RecipesRepository
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock

data class SoftDeletePurgeConfig(
    val enabled: Boolean = false,
    val retentionDays: Long = 90,
    val intervalHours: Long = 24,
    val batchSize: Int = 500,
    val dryRun: Boolean = false
)

class SoftDeletePurgeService(
    private val recipesRepository: RecipesRepository,
    private val log: Logger,
    private val clock: Clock = Clock.System
) {
    suspend fun purgeOnce(config: SoftDeletePurgeConfig): Int {
        if (config.retentionDays <= 0 || config.batchSize <= 0) {
            log.warn("Skipping soft-delete purge due to invalid config: $config")
            return 0
        }

        val cutoffMillis = clock.now().toEpochMilliseconds() - (config.retentionDays * MILLIS_PER_DAY)
        if (config.dryRun) {
            log.info("Soft-delete purge dry run: cutoffMillis=$cutoffMillis batchSize=${config.batchSize}")
            return 0
        }

        val deletedCount = recipesRepository.purgeSoftDeletedRecipes(
            olderThanMillis = cutoffMillis,
            limit = config.batchSize
        )
        log.info("Soft-delete purge finished: deleted=$deletedCount cutoffMillis=$cutoffMillis")
        return deletedCount
    }

    companion object {
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}

