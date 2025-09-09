package com.tenmilelabs.plugins

import com.tenmilelabs.data.RecipesRepository
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

fun Application.configureTemplating(recipeRepository: RecipesRepository) {
    install(Thymeleaf) {
        setTemplateResolver(ClassLoaderTemplateResolver().apply {
            prefix = "templates/thymeleaf/"
            suffix = ".html"
            characterEncoding = "utf-8"
        })
    }
    routing {
        get("/templated/html-thymeleaf") {
            call.respond(
                ThymeleafContent(
                    "index",
                    mapOf("user" to ThymeleafUser(1, "user1"))
                )
            )
        }
        //this is the additional route to add
        get("/templated/recipes") {
            val recipes = recipeRepository.allRecipes()
            call.respond(ThymeleafContent("all-recipes", mapOf("recipes" to recipes)))
        }
    }
}

data class ThymeleafUser(val id: Int, val name: String)