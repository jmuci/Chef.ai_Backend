package com.tenmilelabs.application.service

import com.tenmilelabs.domain.repository.RecipesRepository
import com.tenmilelabs.domain.repository.SyncRepository
import com.tenmilelabs.domain.service.AuthService
import com.tenmilelabs.domain.service.HomeLayoutService
import com.tenmilelabs.domain.service.JwtService
import com.tenmilelabs.domain.service.RecipesService
import com.tenmilelabs.domain.service.SoftDeletePurgeConfig
import com.tenmilelabs.domain.service.SoftDeletePurgeService
import com.tenmilelabs.domain.service.SyncService
import com.tenmilelabs.infrastructure.auth.configureJwtAuth
import com.tenmilelabs.infrastructure.database.*
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresRecipesRepository
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresRefreshTokenRepository
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresSyncRepository
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresUserRepository
import com.tenmilelabs.infrastructure.database.repositoryImpl.RefreshTokenRepository
import com.tenmilelabs.domain.repository.UserRepository
import com.tenmilelabs.presentation.routes.configureRouting
import io.ktor.server.application.*
import kotlinx.coroutines.*
import io.ktor.server.netty.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module(
    recipeRepository: RecipesRepository = PostgresRecipesRepository(log),
    userRepository: UserRepository = PostgresUserRepository(log),
    refreshTokenRepository: RefreshTokenRepository = PostgresRefreshTokenRepository(log),
    syncRepository: SyncRepository = PostgresSyncRepository(),
    homeLayoutService: HomeLayoutService = HomeLayoutService(
        json = Json {
            ignoreUnknownKeys = true
            explicitNulls = true
            encodeDefaults = false
        }
    ),
) {
    val recipeRepository = recipeRepository
    val userRepository = userRepository
    val refreshTokenRepository = refreshTokenRepository

    // Configure JWT settings
    val jwtSecret = environment.config.propertyOrNull("jwt.secret")?.getString() ?: "secret"
    val jwtIssuer = environment.config.propertyOrNull("jwt.issuer")?.getString() ?: "http://0.0.0.0:8080"
    val jwtAudience = environment.config.propertyOrNull("jwt.audience")?.getString() ?: "jwt-audience"

    val jwtService = JwtService(jwtSecret, jwtIssuer, jwtAudience)
    val authService = AuthService(userRepository, refreshTokenRepository, jwtService, log)
    val recipesService = RecipesService(recipeRepository, log)
    val softDeletePurgeService = SoftDeletePurgeService(recipeRepository, log)
    val syncService = SyncService(syncRepository, log)

    // Set up plugins
    configureDatabases()
    configureJwtAuth(jwtService)
    configureRouting(
        recipeRepository = recipeRepository,
        recipesService = recipesService,
        authService = authService,
        syncService = syncService,
        homeLayoutService = homeLayoutService,
    )

    val purgeConfig = softDeletePurgeConfig()
    if (purgeConfig.enabled) {
        val purgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val purgeJob = purgeScope.launch {
            while (isActive) {
                try {
                    softDeletePurgeService.purgeOnce(purgeConfig)
                } catch (ex: Exception) {
                    log.error("Soft-delete purge job failed", ex)
                }
                delay(purgeConfig.intervalHours * MILLIS_PER_HOUR)
            }
        }
        monitor.subscribe(ApplicationStopping) {
            purgeJob.cancel()
            purgeScope.cancel()
        }
        log.info("Soft-delete purge job started with config=$purgeConfig")
    } else {
        log.info("Soft-delete purge job is disabled")
    }
}

private fun Application.softDeletePurgeConfig(): SoftDeletePurgeConfig {
    fun string(path: String): String? = environment.config.propertyOrNull(path)?.getString()
    val retentionDays = string("cleanup.softDelete.retentionDays")?.toLongOrNull()?.takeIf { it > 0 } ?: 90L
    val intervalHours = string("cleanup.softDelete.intervalHours")?.toLongOrNull()?.takeIf { it > 0 } ?: 24L
    val batchSize = string("cleanup.softDelete.batchSize")?.toIntOrNull()?.takeIf { it > 0 } ?: 500

    return SoftDeletePurgeConfig(
        enabled = string("cleanup.softDelete.enabled")?.toBooleanStrictOrNull() ?: false,
        retentionDays = retentionDays,
        intervalHours = intervalHours,
        batchSize = batchSize,
        dryRun = string("cleanup.softDelete.dryRun")?.toBooleanStrictOrNull() ?: false
    )
}

private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
