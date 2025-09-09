package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.domain.model.Label
import com.tenmilelabs.domain.service.RecipesService
import com.tenmilelabs.infrastructure.database.FilterFields
import com.tenmilelabs.infrastructure.database.RecipesRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.acceptItems
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.Thymeleaf
import io.ktor.server.thymeleaf.ThymeleafContent
import io.ktor.util.logging.Logger
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

private const val ACCEPT_APP_JSON = "application/json"

fun Application.configureRouting(recipeRepository: RecipesRepository, recipesService: RecipesService) {
    // Install plugins related to routing
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

        route("/recipes") {

            get {
                handleGetAllRecipes(recipesService)
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
                handleDeleteRecipe(call, log, recipeRepository)
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
    recipeRepository: RecipesRepository
) {
    val id = call.parameters["recipeId"]
    if (id == null) {
        call.respond(HttpStatusCode.BadRequest)
        return
    }

    if (recipeRepository.removeRecipe(uuid = id)) {
        log.info("Successfully removed recipe $id from DB.")
        call.respond(HttpStatusCode.NoContent)
    } else {
        log.info("Recipe with $id not found.")
        call.respond(HttpStatusCode.NotFound)
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
        if (ACCEPT_APP_JSON in accept) {
            call.respond(recipes)
        } else {
            call.respond(ThymeleafContent("recipes-by-label", data))
        }
    } catch (ex: IllegalArgumentException) {
        log.warn(ex.message, ex)
        call.respond(HttpStatusCode.BadRequest)
    }
}

private suspend fun RoutingContext.handleGetAllRecipes(recipesService: RecipesService) {
    val recipes = recipesService.getAllRecipes()
    val accept = call.request.acceptItems().map { it.value }
    if (ACCEPT_APP_JSON in accept) {
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
    val params = call.receiveParameters()
    var parsedLabel = Label.LowCarb
    try {
        parsedLabel= Label.valueOf(params["label"] ?: "")
    } catch (e : IllegalArgumentException) {
        log.error("500: Illegal Argument $params[\"label\"] doesn't match an existing label. " + e.message, e)
        call.respond(HttpStatusCode.BadRequest, "Invalid label parameter!")
        return
    }

    val request = CreateRecipeRequest(
        title = params["title"] ?: "",
        label = parsedLabel,
        description = params["description"] ?: "",
        preparationTimeMinutes = params["preparationTimeMinutes"]?.toInt() ?: 0,
        recipeUrl = params["recipeUrl"] ?: "",
        imageUrl = params["imageUrl"] ?: ""
    )

    // Request-level validation (HTTP-specific)
    if (request.title.length > 100) {
        call.respond(HttpStatusCode.BadRequest, "Title too long (max 100 chars)")
        return
    }
    if (request.title.isBlank() || request.description.isBlank() || request.recipeUrl.isBlank()
        || request.imageUrl.isBlank() || request.preparationTimeMinutes == 0
    ) {
        call.respond(HttpStatusCode.BadRequest, "None of the fields can be blank")
        return
    }
    try {
        val recipe = recipesService.createRecipe(request)
        log.info("Successfully added recipe with params ${recipe.title}.first, ID: ${recipe.uuid}")
        val accept = call.request.acceptItems().map { it.value }
        if (ACCEPT_APP_JSON in accept) {
            call.respond(HttpStatusCode.Created, recipe)
        } else {
            call.respond(
                HttpStatusCode.Created,
                ThymeleafContent("single-recipe", mapOf("recipe" to recipe))
            )
        }
    } catch (ex: IllegalArgumentException) {
        log.warn(ex.message, ex)
        call.respond(HttpStatusCode.BadRequest)
    } catch (ex: Exception) {
        log.warn(ex.message, ex)
        call.respond(HttpStatusCode.BadRequest)
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
    if (ACCEPT_APP_JSON in accept) {
        call.respond(recipe)
    } else {
        call.respond(
            ThymeleafContent("single-recipe", mapOf("recipe" to recipe))
        )
    }
}