package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.domain.repository.FilterFields
import com.tenmilelabs.domain.repository.UserPreferencesRepository
import com.tenmilelabs.domain.service.AuthService
import com.tenmilelabs.domain.service.HomeLayoutService
import com.tenmilelabs.domain.service.HouseholdService
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
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*
import io.ktor.util.logging.*
import kotlinx.serialization.json.Json
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.util.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private const val ACCEPT_APP_JSON = "application/json"
private const val ACCEPT_WILDCARD = "*/*"

fun Application.configureRouting(
    recipesService: RecipesService,
    authService: AuthService,
    syncService: SyncService,
    homeLayoutService: HomeLayoutService,
    mealPlanGenerationService: MealPlanGenerationService,
    recipeImageService: RecipeImageService,
    imageBlobConfig: ImageBlobConfig,
    userPreferencesRepository: UserPreferencesRepository,
    recipeSearchService: RecipeSearchService,
    householdService: HouseholdService,
    householdInviteBaseUrl: String,
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
            // The message can carry internals (constraint names, SQL fragments, paths). Log it,
            // and hand the client only a correlation id — CLAUDE.md's "no stack traces or
            // internal errors" rule.
            val traceId = UUID.randomUUID().toString()
            call.application.log.error("Unhandled IllegalStateException [traceId=$traceId]", cause)
            call.respondText(
                "500: Internal server error (traceId=$traceId)",
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
        register(RECIPE_DETAIL_RATE_LIMIT_NAME) {
            rateLimiter(limit = 30, refillPeriod = 10.seconds)
            // Its own bucket, not RECIPE_SEARCH_RATE_LIMIT_NAME's - see RecipeDetailRoutes.kt.
            // Same anonymous-by-remote-address keying rationale as search.
            requestKey { call -> call.userId ?: call.request.origin.remoteAddress }
        }
        register(AUTH_RATE_LIMIT_NAME) {
            // The /auth routes were the only unthrottled surface left. Login is a credential-
            // stuffing target and register mints a bcrypt cost-12 hash per call, which is an
            // asymmetric DoS on its own. Always keyed by peer address: these routes are
            // unauthenticated by definition, so there is no principal to key on.
            //
            // 20/min rather than something tighter because /auth/refresh shares this bucket, and
            // access tokens last an hour - several devices behind one NAT address (a household,
            // an office) legitimately refresh through the same key. That still caps a stuffing
            // run at a rate no attacker can work with. Same proxy caveat as the search limiter:
            // without XForwardedHeaders this is the proxy's address if one is ever put in front.
            rateLimiter(limit = 20, refillPeriod = 60.seconds)
            requestKey { call -> call.request.origin.remoteAddress }
        }
        register(MEAL_PLAN_GENERATE_RATE_LIMIT_NAME) {
            // Tighter than search/detail: generation does real work (candidate scan + ranking +
            // per-recipe aggregate assembly), not a single indexed lookup. Same anonymous-by-
            // remote-address keying rationale as the other optional-auth routes.
            rateLimiter(limit = 10, refillPeriod = 60.seconds)
            requestKey { call -> call.userId ?: call.request.origin.remoteAddress }
        }
        register(HOUSEHOLD_INVITE_CREATE_RATE_LIMIT_NAME) {
            // Required-auth route; call.userId is always non-null by the time this runs, but keyed
            // defensively the same way RECIPE_IMAGE_UPLOAD_RATE_LIMIT_NAME is.
            rateLimiter(limit = 20, refillPeriod = 1.hours)
            requestKey { call -> call.userId ?: "anonymous" }
        }
        register(HOUSEHOLD_JOIN_RATE_LIMIT_NAME) {
            rateLimiter(limit = 10, refillPeriod = 60.seconds)
            requestKey { call -> call.userId ?: "anonymous" }
        }
        register(HOUSEHOLD_INVITE_PREVIEW_RATE_LIMIT_NAME) {
            // Optional-auth route (a signed-out link tap): same anonymous-by-remote-address
            // rationale as RECIPE_SEARCH_RATE_LIMIT_NAME.
            rateLimiter(limit = 30, refillPeriod = 10.seconds)
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
        // Public authentication routes. Throttled per peer address - see AUTH_RATE_LIMIT_NAME.
        rateLimit(AUTH_RATE_LIMIT_NAME) {
            authRoutes(authService)
        }

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
            // ChefAI#186: a search result's tap-through needs to fetch a not-yet-synced recipe
            // by id under the same anonymous-capable rules search itself uses.
            recipeDetailRoutes(syncService)
            // Anonymous-capable stateless meal-plan generation: closes the 16-vs-789-candidate
            // gap for a signed-out device generating an INCLUDE_PUBLIC plan (see MealPlanRoutes.kt).
            mealPlanGenerationRoutes(mealPlanGenerationService, syncService)
            // The response never depends on caller identity - see the KDoc on
            // householdPreviewRoutes for why this still belongs under optional auth rather than
            // being fully public.
            householdPreviewRoutes(householdService)
        }

        // Protected routes - require authentication
        authenticate("auth-jwt") {
            syncRoutes(syncService)
            mealPlanRoutes(mealPlanGenerationService)
            userPreferencesRoutes(userPreferencesRepository, mealPlanGenerationService)
            recipeImageRoutes(recipeImageService, imageBlobConfig)
            householdRoutes(householdService, householdInviteBaseUrl)
            route("/recipes") {

                get {
                    handleGetAllRecipes(recipesService, call)
                }
                get("/byName") {
                    findRecipeByField(FilterFields.BY_TITLE, call, application.log, recipesService)
                }
                get("/byId") {
                    findRecipeByField(FilterFields.BY_ID, call, application.log, recipesService)
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

/**
 * The caller's id from the validated JWT, or null when the token carries no usable `userId`
 * claim.
 *
 * `call.userId` is a `String?` read straight off the token payload, so the claim can be absent
 * (null) or present but not a UUID. Both used to reach `UUID.fromString` directly here and throw —
 * NPE and IllegalArgumentException respectively — answering 500 on what is really an
 * unauthenticated request, while the `if (userId == null)` guard that followed was dead code
 * (`UUID.fromString` never returns null). SyncRoutes/RecipeImageRoutes/RecipeSearchRoutes each
 * already parse defensively; this is the same idiom for the routes in this file.
 */
private fun RoutingCall.authenticatedUserId(): UUID? =
    try {
        userId?.let(UUID::fromString)
    } catch (_: IllegalArgumentException) {
        null
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

    val userId = call.authenticatedUserId()
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
    val userId = call.authenticatedUserId()
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
    val userId = call.authenticatedUserId()
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

/**
 * Backs `/recipes/byName` and `/recipes/byId`.
 *
 * Goes through [RecipesService] rather than the repository directly. The repository's
 * `recipeByTitle`/`recipeById` apply no visibility predicate at all, so calling them from here
 * let any authenticated caller read any other user's PRIVATE recipe by uuid — or by guessing its
 * title. The service methods apply the same `owned or PUBLIC` rule `handleGetAllRecipes` has
 * always used, and a recipe the caller may not see is reported as 404, not 403, so this cannot be
 * used to probe for a private recipe's existence.
 */
private suspend fun findRecipeByField(
    field: FilterFields,
    call: RoutingCall,
    log: Logger,
    recipesService: RecipesService
) {
    val filterField = call.request.queryParameters[field.label]
    if (filterField == null) {
        log.warn("no $field provided")
        call.respond(HttpStatusCode.BadRequest)
        return
    }

    val userId = call.authenticatedUserId()
    if (userId == null) {
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("User not authenticated"))
        return
    }

    val recipe = when (field) {
        FilterFields.BY_TITLE -> recipesService.getRecipeByTitle(filterField, userId)
        // PostgresRecipesRepository.recipeById parses this straight into a UUID, so a malformed
        // value threw IllegalArgumentException and surfaced as a 500. It's a bad request.
        FilterFields.BY_ID -> {
            val parsed = try {
                UUID.fromString(filterField)
            } catch (_: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("uuid is not a valid UUID"))
                return
            }
            recipesService.getRecipeById(parsed.toString(), userId)
        }
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
