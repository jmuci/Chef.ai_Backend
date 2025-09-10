package com.tenmilelabs.application.service

import com.tenmilelabs.domain.service.RecipesService
import com.tenmilelabs.infrastructure.database.FakeRecipesRepository
import com.tenmilelabs.infrastructure.database.configureDatabases
import com.tenmilelabs.presentation.routes.configureRouting
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val recipesService = RecipesService(FakeRecipesRepository, log)
    // Set up plugins
    //configureSerialization()
    configureDatabases()
    configureRouting(recipeRepository = FakeRecipesRepository, recipesService)
}
