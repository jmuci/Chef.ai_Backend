package com.tenmilelabs

import com.tenmilelabs.api.recipes.getAllRecipes
import com.tenmilelabs.api.recipes.getRecipesByLabel
import com.tenmilelabs.api.recipes.postNewRecipe
import com.tenmilelabs.data.RecipesRepository
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


fun Application.configureRouting(recipeRepository: RecipesRepository) {
    install(StatusPages) {
        exception<IllegalStateException> { call, cause ->
            call.respondText("App in illegal state as ${cause.message}")
        }
    }
    routing {
        staticResources("/recipes-ui", "recipes-ui")
        staticResources("/", "static")

        route("/recipes") {
            getAllRecipes(recipeRepository)

            getRecipesByLabel(recipeRepository)

            postNewRecipe(recipeRepository, application.log)

            get("/error-test") {
                throw IllegalStateException("Too Busy")
            }
        }

    }
}
