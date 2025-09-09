package com.tenmilelabs

import com.tenmilelabs.infrastructure.database.RecipesRepositoryImpl
import com.tenmilelabs.infrastructure.plugins.configureSerialization
import com.tenmilelabs.presentation.routes.configureRouting
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Set up plugins
    configureSerialization()
    configureRouting(recipeRepository = RecipesRepositoryImpl)
}
