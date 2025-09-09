package com.tenmilelabs.plugins

import com.tenmilelabs.data.FilterFields
import com.tenmilelabs.data.RecipesRepository
import com.tenmilelabs.model.Label
import com.tenmilelabs.model.Recipe
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*
import io.ktor.util.logging.*
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.util.*

private const val ACCEPT_APP_JSON = "application/json"

fun Application.configureTemplating(recipeRepository: RecipesRepository) {
    install(Thymeleaf) {
        setTemplateResolver(ClassLoaderTemplateResolver().apply {
            prefix = "templates/thymeleaf/"
            suffix = ".html"
            characterEncoding = "utf-8"
        })
    }
    routing {
        route("/recipes") {
            get {
                val recipes = recipeRepository.allRecipes()
                val accept = call.request.acceptItems().map { it.value }
                if (ACCEPT_APP_JSON in accept) {
                    call.respond(recipeRepository.allRecipes())
                } else {
                    call.respond(ThymeleafContent("all-recipes", mapOf("recipes" to recipes)))
                }
            }
            get("/byName") {
                findRecipeByField(FilterFields.BY_TITLE, call, application.log, recipeRepository)
            }
            get("/byId") {
                findRecipeByField(FilterFields.BY_ID, call, application.log, recipeRepository)
            }
            get("/byLabel") {
                val labelAsText = call.request.queryParameters["label"]
                if (labelAsText == null) {
                    log.warn("no label provided")
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                try {
                    val label = Label.valueOf(labelAsText)
                    val recipes = recipeRepository.recipesByLabel(label)


                    if (recipes.isEmpty()) {
                        log.warn("no recipes found for $label")
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    val data = mapOf(
                        "label" to label,
                        "recipes" to recipes
                    )
                    val accept = call.request.acceptItems().map { it.value }
                    if (ACCEPT_APP_JSON in accept) {
                        call.respond(recipeRepository.allRecipes())
                    } else {
                        call.respond(ThymeleafContent("recipes-by-label", data))
                    }
                } catch (ex: IllegalArgumentException) {
                    log.warn(ex.message, ex)
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
            post {
                val formContent = call.receiveParameters()
                val params = Triple(
                    formContent["title"] ?: "",
                    formContent["description"] ?: "",
                    formContent["label"] ?: ""
                )
                if (params.toList().any { it.isEmpty() }) {
                    log.warn("At least one param is missing, params: $params")
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                try {
                    val label = Label.valueOf(params.third)
                    recipeRepository.addRecipe(
                        Recipe(
                            uuid = UUID.randomUUID().toString(),
                            title = params.first,
                            description = params.second,
                            label = label,
                            preparationTimeMinutes = 40,
                            imageUrlThumbnail = "",
                            imageUrl = "",
                            recipeUrl = ""
                        )
                    )
                    val recipes = recipeRepository.allRecipes()
                    log.info("Successfully added recipe with params $params.first, $params.second, $params.third")
                    val accept = call.request.acceptItems().map { it.value }
                    if (ACCEPT_APP_JSON in accept) {
                        call.respond(HttpStatusCode.Created, recipes)
                    } else {
                        call.respond(
                            HttpStatusCode.Created,
                            ThymeleafContent("all-recipes", mapOf("recipes" to recipes))
                        )
                    }
                } catch (ex: IllegalArgumentException) {
                    log.warn(ex.message, ex)
                    call.respond(HttpStatusCode.BadRequest)
                } catch (ex: IllegalStateException) {
                    log.warn(ex.message, ex)
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
        }

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
