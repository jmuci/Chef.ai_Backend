package com.tenmilelabs.plugins

import com.tenmilelabs.data.RecipesRepository
import com.tenmilelabs.model.Label
import com.tenmilelabs.model.Recipe
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.util.UUID

fun Application.configureTemplating(recipeRepository: RecipesRepository) {
    install(Thymeleaf) {
        setTemplateResolver(ClassLoaderTemplateResolver().apply {
            prefix = "templates/thymeleaf/"
            suffix = ".html"
            characterEncoding = "utf-8"
        })
    }
    routing {
        // TODO Those endpoints duplicate those in RecipesEnpoints.
        // Ideally merge them based on the content type? eg. respond JSON for API calls and rendered
        // Templates for web views
        route("/templated/recipes") {
            get {
                val recipes = recipeRepository.allRecipes()
                call.respond(ThymeleafContent("all-recipes", mapOf("recipes" to recipes)))
            }
            get("/byName") {
                val title = call.request.queryParameters["title"]
                if (title == null) {
                    log.warn("no title provided")
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                val recipe = recipeRepository.recipeByTitle(title)
                if (recipe == null) {
                    log.warn("no recipe found for $title")
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                call.respond(
                    ThymeleafContent("single-recipe", mapOf("recipe" to recipe))
                )
            }
            get("/byId") {
                val recipeId = call.request.queryParameters["recipeId"]
                if (recipeId == null) {
                    log.warn("no recipeId provided")
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                val recipe = recipeRepository.recipeById(recipeId)
                if (recipe == null) {
                    log.warn("no recipe found for $recipeId")
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                try {

                    call.respond(
                        ThymeleafContent("single-recipe", mapOf("recipe" to recipe))
                    )
                } catch (e: Exception) {
                    log.error("failed to get recipe for $recipeId", e)
                }
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
                    call.respond(ThymeleafContent("recipes-by-label", data))
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
                    call.respond(
                        HttpStatusCode.Created,
                        ThymeleafContent("all-recipes", mapOf("recipes" to recipes))
                    )
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
