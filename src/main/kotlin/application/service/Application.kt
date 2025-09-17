package com.tenmilelabs.application.service

import com.tenmilelabs.domain.service.RecipesService
import com.tenmilelabs.infrastructure.database.PostgresRecipesRepository
import com.tenmilelabs.infrastructure.database.RecipesRepository
import com.tenmilelabs.infrastructure.database.configureDatabases
import com.tenmilelabs.presentation.routes.configureRouting
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module(
    recipeRepository: RecipesRepository = PostgresRecipesRepository(log),
) {
    val recipeRepository = recipeRepository
    val recipesService = RecipesService(recipeRepository, log)

    // Set up plugins
    configureDatabases()
    configureRouting(recipeRepository = recipeRepository, recipesService)
}
