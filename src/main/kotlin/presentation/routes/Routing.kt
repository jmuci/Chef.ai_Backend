package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.domain.repository.FilterFields
import com.tenmilelabs.domain.repository.RecipesRepository
import com.tenmilelabs.domain.repository.UserPreferencesRepository
import com.tenmilelabs.domain.service.AuthService
import com.tenmilelabs.domain.service.HomeLayoutService
import com.tenmilelabs.domain.service.ImageBlobConfig
import com.tenmilelabs.domain.service.MealPlanGenerationService
import com.tenmilelabs.domain.service.RecipeImageService
import com.tenmilelabs.domain.service.RecipeSearchService
import com.tenmilelabs.domain.service.RecipesService
import com.tenmilelabs.domain.service.SyncService
import com.tenmilelabs.infrastructure.auth.userId
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*
import io.ktor.util.logging.*
import kotlinx.serialization.json.Json
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.util.*
import kotlin.time.Duration.Companion.seconds

private const val ACCEPT_APP_JSON = "application/json"
private const val ACCEPT_WILDCARD = "*/*"

fun Application.configureRouting(
    recipeRepository: RecipesRepository,
    recipesService: RecipesService,
    authService: AuthService,
    syncService: SyncService,
    homeLayoutService: HomeLayoutService,
    mealPlanGenerationService: MealPlanGenerationService,
    recipeImageService: RecipeImageService,
    imageBlobConfig: ImageBlobConfig,
    userPreferencesRepository: UserPreferencesRepository,
    recipeSearchService: RecipeSearchService,
) {
    // Install plugins related to routing
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = true
                encodeDefaults = false
            }
        )
    }
    install(StatusPages) {
        exception<IllegalStateException> { call, cause ->
            call.respondText(
                "500: App in illegal state as ${cause.message}",
                status = HttpStatusCode.InternalServerError
            )
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respondText(text = "404: Resource Not Found", status = status)
        }
    }
    install(Thymeleaf) {
        setTemplateResolver(ClassLoaderTemplateResolver().apply {
            prefix = "templates/thymeleaf/"
            suffix = ".html"
            characterEncoding = "utf-8"
        })
    }
    install(RateLimit) {
        register(RECIPE_IMAGE_UPLOAD_RATE_LIMIT_NAME) {
            rateLimiter(limit = imageBlobConfig.uploadRateLimitPerMinute, refillPeriod = 60.seconds)
            requestKey { call -> call.userId ?: "anonymous" }
        }
        register(RECIPE_SEARCH_RATE_LIMIT_NAME) {
            rateLimiter(limit = 30, refillPeriod = 10.seconds)
            // Unlike the upload limiter above, this endpoint serves anonymous callers (see the
            // optional-auth block below), so a literal "anonymous" key would put every signed-out
            // device on the planet in one shared 30-per-10s bucket. Key on the socket peer
            // instead. Caveat: that is the proxy's address if one is ever put in front of the
            // JVM, which would need XForwardedHeaders (io.ktor:ktor-server-forwarded-header, not
            // a dependency here) to see through. Today nothing fronts it - docker-compose.yaml
            // publishes 8080 directly.
            requestKey { call -> call.userId ?: call.request.origin.remoteAddress }
        }
    }

    routing {
        application.log.info("Setting up routes")
        staticResources("/recipes-ui", "recipes-ui")
        staticResources("/", "static")

        get("/health") { // Docker health check
            call.respondText("OK")
        }
        // Public authentication routes
        authRoutes(authService)

        // Public: the anonymous-first Home screen must load before any account exists.
        // homeRoutes reads no auth principal, so it belongs outside authenticate("auth-jwt").
        homeRoutes(homeLayoutService)

        // Anonymous-capable, but not principal-free the way homeRoutes is: with optional = true a
        // valid token still produces a JWTPrincipal, so a signed-in user keeps seeing their own
        // PRIVATE recipes in results, while a request carrying no Authorization header is scoped
        // to PUBLIC recipes instead of being challenged. See ChefAI#184 - the Android client
        // skipped search entirely for anonymous sessions precisely because this 401'd on every
        // keystroke.
        authenticate("auth-jwt", optional = true) {
            recipeSearchRoutes(recipeSearchService)
        }

        // Protected routes - require authentication
        authenticate("auth-jwt") {
            syncRoutes(syncService)
            mealPlanRoutes(mealPlanGenerationService)
            userPreferencesRoutes(userPreferencesRepository, mealPlanGenerationService)
            recipeImageRoutes(recipeImageService, imageBlobConfig)
            route("/recipes") {

                get {
                    handleGetAllRecipes(recipesService, call)
                }
                get("/byName") {
                    findRecipeByField(FilterFields.BY_TITLE, call, application.log, recipeRepository)
                }
                get("/byId") {
                    findRecipeByField(FilterFields.BY_ID, call, application.log, recipeRepository)
                }
                post {
                    handlePostNewRecipe(call, application.log, recipesService)
                }
                delete {
                    handleDeleteRecipe(call, log, recipesService)
                }
            }
        }
    }
}

private suspend fun handleDeleteRecipe(
    call: RoutingCall,
    log: Logger,
    recipesService: RecipesService
) {
    val id = call.parameters["uuid"]
    if (id == null) {
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Recipe ID is required"))
        return
    }

    val userId = UUID.fromString(call.userId)
    if (userId == null) {
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("User not authenticated"))
        return
    }

    if (recipesService.deleteRecipe(id, userId)) {
        log.info("Successfully removed recipe $id from DB by user $userId.")
        call.respond(HttpStatusCode.NoContent)
    } else {
        log.info("Recipe with $id not found or user $userId not authorized.")
        call.respond(HttpStatusCode.NotFound, ErrorResponse("Recipe not found or not authorized"))
    }
}

private suspend fun RoutingContext.handleGetAllRecipes(recipesService: RecipesService, call: RoutingCall) {
    val userId = UUID.fromString(call.userId)
    if (userId == null) {
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("User not authenticated"))
        return
    }

    // Get recipes accessible to this user (their own + public ones)
    val recipes = recipesService.getAccessibleRecipes(userId)
    val accept = call.request.acceptItems().map { it.value }
    if (ACCEPT_APP_JSON in accept || ACCEPT_WILDCARD in accept) {
        call.respond(recipes)
    } else {
        call.respond(ThymeleafContent("all-recipes", mapOf("recipes" to recipes)))
    }
}

private suspend fun handlePostNewRecipe(
    call: RoutingCall,
    log: Logger,
    recipesService: RecipesService
) {
    val userId = UUID.fromString(call.userId)
    if (userId == null) {
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("User not authenticated"))
        return
    }

    try {
        val createRecipeRequest = call.receive<CreateRecipeRequest>()
        val recipeResponse = recipesService.createRecipe(createRecipeRequest, userId)
        if (recipeResponse != null) {
            log.info("Successfully added recipe with params ${createRecipeRequest.title}.first, ID: ${recipeResponse.uuid} by user $userId")
            call.respond(HttpStatusCode.Created, recipeResponse)
        } else {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to create recipe"))
            log.warn("Failed to add recipe! Title:  ${createRecipeRequest.title}")
        }
    } catch (ex: IllegalArgumentException) {
        log.warn(ex.message, ex)
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid recipe data"))
    } catch (ex: Exception) {
        log.warn(ex.message, ex)
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to create recipe"))
    }
}

private suspend fun findRecipeByField(
    field: FilterFields,
    call: RoutingCall,
    log: Logger,
    recipeRepository: RecipesRepository
) {
    val filterField = call.request.queryParameters[field.label]
    if (filterField == null) {
        log.warn("no $field provided")
        call.respond(HttpStatusCode.BadRequest)
        return
    }

    val recipe = when (field) {
        FilterFields.BY_TITLE -> recipeRepository.recipeByTitle(filterField)
        FilterFields.BY_ID -> recipeRepository.recipeById(filterField)
    }
    if (recipe == null) {
        log.warn("No recipe found for $filterField")
        call.respond(HttpStatusCode.NotFound)
        return
    }
    val accept = call.request.acceptItems().map { it.value }
    if (ACCEPT_APP_JSON in accept || ACCEPT_WILDCARD in accept) {
        call.respond(recipe)
    } else {
        call.respond(
            ThymeleafContent("single-recipe", mapOf("recipe" to recipe))
        )
    }
}
