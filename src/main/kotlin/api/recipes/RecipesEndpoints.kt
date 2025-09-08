package com.tenmilelabs.api.recipes

import com.tenmilelabs.data.RecipesRepository
import com.tenmilelabs.model.Label
import com.tenmilelabs.model.Recipe
import com.tenmilelabs.model.recipesAsTable
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.util.logging.Logger
import java.util.UUID
import kotlin.text.isEmpty

fun Route.getAllRecipes(recipeRepository: RecipesRepository) {
    get {
        val text = recipeRepository.allRecipes().recipesAsTable()
        val type = ContentType.parse("text/html")
        call.respondText(text, type)
    }
}

fun Route.getRecipesByLabel(recipeRepository: RecipesRepository) {
    get("/byLabel/{label?}") {
        val labelAsText = call.parameters["label"]
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

            call.respondText(
                contentType = ContentType.parse("text/html"),
                text = recipes.recipesAsTable()
            )
        } catch (ex: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest)
        }
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

        if (params.toList().any { it.isEmpty() }) {
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

            log.info("Received POST request on /recipes. Succesfully saved recipe to DB.")
            call.respond(HttpStatusCode.NoContent)
        } catch (ex: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest)
        } catch (ex: IllegalStateException) {
            call.respond(HttpStatusCode.BadRequest)
        }
    }
}


