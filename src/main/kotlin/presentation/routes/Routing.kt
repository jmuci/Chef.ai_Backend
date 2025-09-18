package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.domain.model.Label
import com.tenmilelabs.domain.service.AuthService
import com.tenmilelabs.domain.service.RecipesService
import com.tenmilelabs.infrastructure.auth.userId
import com.tenmilelabs.infrastructure.database.FilterFields
import com.tenmilelabs.infrastructure.database.RecipesRepository
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*
import io.ktor.util.logging.*
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

private const val ACCEPT_APP_JSON = "application/json"
private const val ACCEPT_WILDCARD = "*/*"

fun Application.configureRouting(
    recipeRepository: RecipesRepository,
    recipesService: RecipesService,
    authService: AuthService
) {
    // Install plugins related to routing
    install(ContentNegotiation) {
        json()
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

    routing {
        application.log.info("Setting up routes")
        staticResources("/recipes-ui", "recipes-ui")
        staticResources("/", "static")

        // Public authentication routes
        authRoutes(authService)

        // Protected routes - require authentication
        authenticate("auth-jwt") {
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
                get("/byLabel") {
                    handleGetRecipesByLabel(call, application.log, recipesService)
                }
                post {
                    handlePostNewRecipe(call, application.log, recipesService)
                }
                delete {
                    handleDeleteRecipe(call, log, recipesService)
                }
            }
        }

        get("/error-test") {
            throw IllegalStateException("Too Busy")
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

    val userId = call.userId
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

private suspend fun handleGetRecipesByLabel(
    call: RoutingCall,
    log: Logger,
    recipesService: RecipesService
) {
    val labelAsText = call.request.queryParameters["label"]
    if (labelAsText == null) {
        log.warn("no label provided")
        call.respond(HttpStatusCode.BadRequest)
        return
    }
    try {
        val label = Label.valueOf(labelAsText)
        val recipes = recipesService.getRecipesByLabel(label)
        if (recipes.isEmpty()) {
            log.warn("no recipes found for $label")
            call.respond(HttpStatusCode.NotFound)
            return
        }
        val data = mapOf(
            "label" to label,
            "recipes" to recipes
        )
        val accept = call.request.acceptItems().map { it.value }
        if (ACCEPT_APP_JSON in accept || ACCEPT_WILDCARD in accept) {
            call.respond(recipes)
        } else {
            call.respond(ThymeleafContent("recipes-by-label", data))
        }
    } catch (ex: IllegalArgumentException) {
        log.warn(ex.message, ex)
        call.respond(HttpStatusCode.BadRequest)
    }
}

private suspend fun RoutingContext.handleGetAllRecipes(recipesService: RecipesService, call: RoutingCall) {
    val userId = call.userId
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
    val userId = call.userId
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