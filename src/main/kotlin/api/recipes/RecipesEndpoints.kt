package com.tenmilelabs.api.recipes

import com.tenmilelabs.data.RecipesRepository
import com.tenmilelabs.model.Label
import com.tenmilelabs.model.Recipe
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.util.logging.Logger
import java.util.UUID
import kotlin.text.isEmpty

fun Route.getAllRecipes(recipeRepository: RecipesRepository) {
    get {
        call.respond(recipeRepository.allRecipes())
    }
}

fun Route.getRecipesByLabel(recipeRepository: RecipesRepository, log: Logger) {
    get("/byLabel/{label?}") {
        val labelAsText = call.parameters["label"]
        log.info("Getting recipe by label: $labelAsText")
        if (labelAsText == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }

        try {
            val label = Label.valueOf(labelAsText)
            val recipes = recipeRepository.recipesByLabel(label)

            if (recipes.isEmpty()) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            call.respond(recipes)
        } catch (ex: IllegalArgumentException) {
            log.warn(ex.message, ex)
            call.respond(HttpStatusCode.BadRequest)
        }
    }
}
fun Route.getRecipesById(recipeRepository: RecipesRepository, log: Logger) {
    get("/byId/{recipeId?}") {
        val id = call.parameters["recipeId"]
        log.info("Getting recipe byId with id: $id")
        if (id == null) {
            log.warn("Missing id param in getRecipesById request")
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }

        val recipe = recipeRepository.recipeById(id)
        if (recipe == null) {
            log.info("No recipe with id $id found")
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.respond(recipe)
    }
}

fun Route.getRecipesByName(recipeRepository: RecipesRepository, log: Logger) {
    get("/byName/{title?}") {
        val title = call.parameters["title"]
        log.info("Getting recipe by name: $title")
        if (title == null) {
            log.warn("Missing title param in getRecipesByName request")
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }

        val recipe = recipeRepository.recipeByTitle(title)
        if (recipe == null) {
            log.info("No recipe with title $title found")
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.respond(recipe)
    }
}

fun Route.postNewRecipe(recipeRepository: RecipesRepository, log: Logger) {
    post {
        val formContent = call.receiveParameters()

        val params = Triple(
            formContent["title"] ?: "",
            formContent["description"] ?: "",
            formContent["label"] ?: ""
        )
        log.info("Posting new recipe with params: $params")
        if (params.toList().any { it.isEmpty() }) {
            log.warn("Found empty parameter. Invalid posting new recipe call")
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }
        // TODO Let Serialization do the work once we send a full recipe object
        // RecipesRepository.addRecipe(call.receive<Recipe>())
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

            log.info("Received POST request on /recipes, with title ${params.first}. Successfully saved recipe to DB.")
            call.respond(HttpStatusCode.Created)
        } catch (ex: IllegalArgumentException) {
            log.warn(ex.message, ex)
            call.respond(HttpStatusCode.BadRequest)
        } catch (ex: IllegalStateException) {
            log.warn(ex.message, ex)
            call.respond(HttpStatusCode.BadRequest)
        }
    }
}

fun Route.deleteRecipeById(recipeRepository: RecipesRepository, log: Logger) {
    delete("/{recipeId") {
        val id = call.parameters["recipeId"]
        log.info("Deleting recipe by id: $id")
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@delete
        }

        if (recipeRepository.removeRecipe(uuid = id)) {
            log.info("Successfully removed recipe $id from DB.")
            call.respond(HttpStatusCode.NoContent)
        } else {
            log.info("Recipe with $id not found.")
            call.respond(HttpStatusCode.NotFound)
        }
    }
}

