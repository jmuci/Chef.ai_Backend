package com.tenmilelabs.application.service

import com.tenmilelabs.domain.service.AuthService
import com.tenmilelabs.domain.service.JwtService
import com.tenmilelabs.domain.service.RecipesService
import com.tenmilelabs.infrastructure.auth.configureJwtAuth
import com.tenmilelabs.infrastructure.database.PostgresRecipesRepository
import com.tenmilelabs.infrastructure.database.PostgresUserRepository
import com.tenmilelabs.infrastructure.database.RecipesRepository
import com.tenmilelabs.infrastructure.database.UserRepository
import com.tenmilelabs.infrastructure.database.configureDatabases
import com.tenmilelabs.presentation.routes.configureRouting
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module(
    recipeRepository: RecipesRepository = PostgresRecipesRepository(log),
    userRepository: UserRepository = PostgresUserRepository(log)
) {
    val recipeRepository = recipeRepository
    val userRepository = userRepository

    // Configure JWT settings
    val jwtSecret = environment.config.propertyOrNull("jwt.secret")?.getString() ?: "secret"
    val jwtIssuer = environment.config.propertyOrNull("jwt.issuer")?.getString() ?: "http://0.0.0.0:8080"
    val jwtAudience = environment.config.propertyOrNull("jwt.audience")?.getString() ?: "jwt-audience"

    val jwtService = JwtService(jwtSecret, jwtIssuer, jwtAudience)
    val authService = AuthService(userRepository, jwtService, log)
    val recipesService = RecipesService(recipeRepository, log)

    // Set up plugins
    configureDatabases()
    configureJwtAuth(jwtService)
    configureRouting(recipeRepository = recipeRepository, recipesService, authService)
}
