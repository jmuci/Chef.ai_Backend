package com.tenmilelabs.application.service

import com.tenmilelabs.domain.repository.RecipesRepository
import com.tenmilelabs.domain.service.AuthService
import com.tenmilelabs.domain.service.JwtService
import com.tenmilelabs.domain.service.RecipesService
import com.tenmilelabs.infrastructure.auth.configureJwtAuth
import com.tenmilelabs.infrastructure.database.*
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresRecipesRepository
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresRefreshTokenRepository
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresUserRepository
import com.tenmilelabs.infrastructure.database.repositoryImpl.RefreshTokenRepository
import com.tenmilelabs.infrastructure.database.repositoryImpl.UserRepository
import com.tenmilelabs.presentation.routes.configureRouting
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module(
    recipeRepository: RecipesRepository = PostgresRecipesRepository(log),
    userRepository: UserRepository = PostgresUserRepository(log),
    refreshTokenRepository: RefreshTokenRepository = PostgresRefreshTokenRepository(log)
) {
    val recipeRepository = recipeRepository
    val userRepository = userRepository
    val refreshTokenRepository = refreshTokenRepository

    // Configure JWT settings
    val jwtSecret = environment.config.propertyOrNull("jwt.secret")?.getString() ?: "secret"
    val jwtIssuer = environment.config.propertyOrNull("jwt.issuer")?.getString() ?: "http://0.0.0.0:8080"
    val jwtAudience = environment.config.propertyOrNull("jwt.audience")?.getString() ?: "jwt-audience"

    val jwtService = JwtService(jwtSecret, jwtIssuer, jwtAudience)
    val authService = AuthService(userRepository, refreshTokenRepository, jwtService, log)
    val recipesService = RecipesService(recipeRepository, log)

    // Set up plugins
    configureDatabases()
    configureJwtAuth(jwtService)
    configureRouting(recipeRepository = recipeRepository, recipesService, authService)
}
