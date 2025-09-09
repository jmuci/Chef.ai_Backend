package com.tenmilelabs

import com.tenmilelabs.data.RecipesRepository
import com.tenmilelabs.plugins.configureRouting
import com.tenmilelabs.plugins.configureSerialization
import com.tenmilelabs.plugins.configureTemplating
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Set up plugins
    configureSerialization()
    configureRouting(recipeRepository = RecipesRepository)
    configureTemplating(recipeRepository = RecipesRepository)
}
