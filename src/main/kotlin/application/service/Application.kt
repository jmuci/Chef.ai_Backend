package com.tenmilelabs.application.service

import com.tenmilelabs.domain.repository.BlobStore
import com.tenmilelabs.domain.repository.HouseholdRepository
import com.tenmilelabs.domain.repository.ImageBlobRepository
import com.tenmilelabs.domain.repository.RecipeSearchRepository
import com.tenmilelabs.domain.repository.RecipesRepository
import com.tenmilelabs.domain.repository.SyncRepository
import com.tenmilelabs.domain.repository.UserPreferencesRepository
import com.tenmilelabs.domain.service.AuthService
import com.tenmilelabs.domain.service.HomeLayoutService
import com.tenmilelabs.domain.service.HouseholdService
import com.tenmilelabs.domain.service.ImageBlobConfig
import com.tenmilelabs.domain.service.ImageBlobReclamationConfig
import com.tenmilelabs.domain.service.ImageBlobReclamationService
import com.tenmilelabs.domain.service.JwtService
import com.tenmilelabs.domain.service.MealPlanGenerationService
import com.tenmilelabs.domain.service.RecipeImageService
import com.tenmilelabs.domain.service.RecipeSearchService
import com.tenmilelabs.domain.service.RecipesService
import com.tenmilelabs.domain.service.SoftDeletePurgeConfig
import com.tenmilelabs.domain.service.SoftDeletePurgeService
import com.tenmilelabs.domain.service.SyncService
import com.tenmilelabs.infrastructure.auth.configureJwtAuth
import com.tenmilelabs.infrastructure.database.*
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresHouseholdRepository
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresImageBlobRepository
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresRecipeSearchRepository
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
    recipeSearchRepository: RecipeSearchRepository = PostgresRecipeSearchRepository(),
    householdRepository: HouseholdRepository = PostgresHouseholdRepository(),
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
    val jwtSecret = resolveJwtSecret(
        configured = environment.config.propertyOrNull("jwt.secret")?.getString(),
        developmentMode = developmentMode,
        log = log,
    )
    val jwtIssuer = environment.config.propertyOrNull("jwt.issuer")?.getString() ?: "http://0.0.0.0:8080"
    val jwtAudience = environment.config.propertyOrNull("jwt.audience")?.getString() ?: "jwt-audience"

    val jwtService = JwtService(jwtSecret, jwtIssuer, jwtAudience)
    val authService = AuthService(userRepository, refreshTokenRepository, jwtService, log)
    val recipesService = RecipesService(recipeRepository, log)
    val softDeletePurgeService = SoftDeletePurgeService(recipeRepository, log)
    val syncService = SyncService(syncRepository, log, userPreferencesRepository)

    val recipeSearchService = RecipeSearchService(recipeSearchRepository)
    val householdService = HouseholdService(householdRepository, userRepository, log)
    val householdInviteBaseUrl = environment.config.propertyOrNull("household.inviteBaseUrl")?.getString()
        ?: "https://chefai.app/invite"

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
        recipesService = recipesService,
        authService = authService,
        syncService = syncService,
        homeLayoutService = homeLayoutService,
        mealPlanGenerationService = resolvedMealPlanGenerationService,
        recipeImageService = recipeImageService,
        imageBlobConfig = imageBlobConfig,
        userPreferencesRepository = userPreferencesRepository,
        recipeSearchService = recipeSearchService,
        householdService = householdService,
        householdInviteBaseUrl = householdInviteBaseUrl,
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
                try {
                    // deleteExpiredTokens() was implemented but never called, so hashed refresh
                    // tokens accumulated for the life of the deployment. Rides along with the
                    // existing sweep rather than starting a third coroutine scope; caught
                    // separately so a failure here doesn't skip the recipe purge next tick.
                    val deleted = refreshTokenRepository.deleteExpiredTokens()
                    if (deleted > 0) log.info("Expired refresh token purge removed $deleted token(s)")
                } catch (ex: Exception) {
                    log.error("Expired refresh token purge failed", ex)
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

/**
 * The signing key used for access tokens in development, when no real secret is configured.
 *
 * Only ever reachable with `developmentMode = true` (which `testApplication` sets, and which
 * production never does). It is a constant on purpose: it must be obviously worthless, so that a
 * token minted under it is never mistaken for a real one.
 */
internal const val DEVELOPMENT_JWT_SECRET = "development-only-insecure-jwt-secret-do-not-deploy"

/** Shortest secret accepted in production. HMAC-SHA256's key should be at least its 256-bit block. */
internal const val MIN_JWT_SECRET_LENGTH = 32

/**
 * Secrets that have been published in this repository and must never sign a real token again.
 * Checked explicitly rather than left to the length rule, because rejecting them by accident (one
 * happens to be long enough) is exactly the kind of thing that regresses silently.
 */
internal val PUBLISHED_JWT_SECRETS = setOf(
    "secret",
    "your-secret-key-change-this-in-production",
)

/**
 * Resolves the JWT signing secret, refusing to start rather than falling back to a guessable one.
 *
 * This used to be `config["jwt.secret"] ?: "secret"`, with `application.yaml` shipping a literal
 * placeholder — so every deployment signed tokens with a string published in the repository, and
 * anyone holding it could mint a token for any `userId`. There is no safe default for this value,
 * so an unusable one is a startup failure in production and a loud warning in development.
 *
 * Kept as a pure function (rather than reading config inline) so the rules are unit-testable
 * without booting an application.
 */
internal fun resolveJwtSecret(
    configured: String?,
    developmentMode: Boolean,
    log: org.slf4j.Logger? = null,
): String {
    val secret = configured?.trim().orEmpty()
    val rejection = when {
        secret.isEmpty() -> "no jwt.secret is configured"
        secret in PUBLISHED_JWT_SECRETS -> "jwt.secret is a placeholder published in this repository"
        secret.length < MIN_JWT_SECRET_LENGTH ->
            "jwt.secret is shorter than $MIN_JWT_SECRET_LENGTH characters"
        else -> null
    } ?: return secret

    check(developmentMode) {
        "Refusing to start: $rejection. Set the JWT_SECRET environment variable to a random " +
            "value of at least $MIN_JWT_SECRET_LENGTH characters " +
            "(e.g. `openssl rand -base64 48`). See docs/auth-architecture.md."
    }
    log?.warn(
        "$rejection - falling back to the insecure development signing key. " +
            "Tokens issued now are forgeable by anyone; never use this outside local development."
    )
    return DEVELOPMENT_JWT_SECRET
}
