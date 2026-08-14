package com.tenmilelabs.application.service

import com.tenmilelabs.domain.repository.BlobStore
import com.tenmilelabs.domain.repository.ImageBlobRepository
import com.tenmilelabs.domain.repository.RecipesRepository
import com.tenmilelabs.domain.repository.SyncRepository
import com.tenmilelabs.domain.repository.UserPreferencesRepository
import com.tenmilelabs.domain.service.AuthService
import com.tenmilelabs.domain.service.HomeLayoutService
import com.tenmilelabs.domain.service.ImageBlobConfig
import com.tenmilelabs.domain.service.ImageBlobReclamationConfig
import com.tenmilelabs.domain.service.ImageBlobReclamationService
import com.tenmilelabs.domain.service.JwtService
import com.tenmilelabs.domain.service.MealPlanGenerationService
import com.tenmilelabs.domain.service.RecipeImageService
import com.tenmilelabs.domain.service.RecipesService
import com.tenmilelabs.domain.service.SoftDeletePurgeConfig
import com.tenmilelabs.domain.service.SoftDeletePurgeService
import com.tenmilelabs.domain.service.SyncService
import com.tenmilelabs.infrastructure.auth.configureJwtAuth
import com.tenmilelabs.infrastructure.database.*
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresImageBlobRepository
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresRecipesRepository
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresRefreshTokenRepository
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresSyncRepository
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresUserPreferencesRepository
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresUserRepository
import com.tenmilelabs.infrastructure.database.repositoryImpl.RefreshTokenRepository
import com.tenmilelabs.infrastructure.storage.LocalDiskBlobStore
import com.tenmilelabs.domain.repository.UserRepository
import com.tenmilelabs.presentation.routes.configureRouting
import io.ktor.server.application.*
import kotlinx.coroutines.*
import io.ktor.server.netty.*
import kotlinx.serialization.json.Json
import java.nio.file.Path

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module(
    recipeRepository: RecipesRepository = PostgresRecipesRepository(log),
    userRepository: UserRepository = PostgresUserRepository(log),
    refreshTokenRepository: RefreshTokenRepository = PostgresRefreshTokenRepository(log),
    syncRepository: SyncRepository = PostgresSyncRepository(),
    userPreferencesRepository: UserPreferencesRepository = PostgresUserPreferencesRepository(),
    imageBlobRepository: ImageBlobRepository = PostgresImageBlobRepository(),
    blobStore: BlobStore = LocalDiskBlobStore(
        Path.of(System.getenv("IMAGE_BLOB_STORAGE_ROOT") ?: "data/image-blobs")
    ),
    homeLayoutService: HomeLayoutService = HomeLayoutService(
        json = Json {
            ignoreUnknownKeys = true
            explicitNulls = true
            encodeDefaults = false
        },
        log = log,
    ),
    mealPlanGenerationService: MealPlanGenerationService? = null,
    configureDatabase: Boolean = true,
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
    val syncService = SyncService(syncRepository, log, userPreferencesRepository)

    val imageBlobConfig = imageBlobConfig()
    val recipeImageService = RecipeImageService(imageBlobRepository, blobStore, imageBlobConfig, log)
    val imageBlobReclamationService =
        ImageBlobReclamationService(imageBlobRepository, blobStore, imageBlobConfig, log)

    val resolvedMealPlanGenerationService = mealPlanGenerationService ?: run {
        val generationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        monitor.subscribe(ApplicationStopping) { generationScope.cancel() }
        MealPlanGenerationService(syncRepository, generationScope, log)
    }

    // Set up plugins
    if (configureDatabase) {
        configureDatabases()
    }
    configureJwtAuth(jwtService)
    configureRouting(
        recipeRepository = recipeRepository,
        recipesService = recipesService,
        authService = authService,
        syncService = syncService,
        homeLayoutService = homeLayoutService,
        mealPlanGenerationService = resolvedMealPlanGenerationService,
        recipeImageService = recipeImageService,
        imageBlobConfig = imageBlobConfig,
        userPreferencesRepository = userPreferencesRepository,
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

    val reclamationConfig = imageBlobReclamationConfig()
    if (reclamationConfig.enabled) {
        val reclamationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val reclamationJob = reclamationScope.launch {
            while (isActive) {
                try {
                    imageBlobReclamationService.sweepOnce(reclamationConfig.batchSize)
                } catch (ex: Exception) {
                    log.error("Image blob reclamation job failed", ex)
                }
                delay(reclamationConfig.intervalHours * MILLIS_PER_HOUR)
            }
        }
        monitor.subscribe(ApplicationStopping) {
            reclamationJob.cancel()
            reclamationScope.cancel()
        }
        log.info("Image blob reclamation job started with config=$reclamationConfig")
    } else {
        log.info("Image blob reclamation job is disabled")
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

private fun Application.imageBlobConfig(): ImageBlobConfig {
    fun string(path: String): String? = environment.config.propertyOrNull(path)?.getString()

    return ImageBlobConfig(
        allowScrapedImageUpload = string("imageBlob.allowScrapedImageUpload")?.toBooleanStrictOrNull() ?: true,
        serveScrapedBlobsToNonOwners =
            string("imageBlob.serveScrapedBlobsToNonOwners")?.toBooleanStrictOrNull() ?: true,
        maxUploadBytes = string("imageBlob.maxUploadBytes")?.toLongOrNull()?.takeIf { it > 0 }
            ?: (5L * 1024 * 1024),
        perUserQuotaBytes = string("imageBlob.perUserQuotaBytes")?.toLongOrNull()?.takeIf { it > 0 }
            ?: (500L * 1024 * 1024),
        unreferencedRetentionDays = string("imageBlob.unreferencedRetentionDays")?.toIntOrNull()?.takeIf { it > 0 }
            ?: 30,
        uploadRateLimitPerMinute = string("imageBlob.uploadRateLimitPerMinute")?.toIntOrNull()?.takeIf { it > 0 }
            ?: 60
    )
}

private fun Application.imageBlobReclamationConfig(): ImageBlobReclamationConfig {
    fun string(path: String): String? = environment.config.propertyOrNull(path)?.getString()
    val intervalHours = string("imageBlob.reclamation.intervalHours")?.toLongOrNull()?.takeIf { it > 0 } ?: 24L
    val batchSize = string("imageBlob.reclamation.batchSize")?.toIntOrNull()?.takeIf { it > 0 } ?: 100

    return ImageBlobReclamationConfig(
        enabled = string("imageBlob.reclamation.enabled")?.toBooleanStrictOrNull() ?: false,
        intervalHours = intervalHours,
        batchSize = batchSize
    )
}

private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
