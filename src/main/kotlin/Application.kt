package com.tenmilelabs

import com.tenmilelabs.data.RecipesRepository
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Set up plugins
    configureSerialization()
    configureRouting(recipeRepository = RecipesRepository)
}
