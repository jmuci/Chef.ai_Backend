package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.application.dto.LoginRequest
import com.tenmilelabs.application.dto.RefreshTokenRequest
import com.tenmilelabs.application.dto.RegisterRequest
import com.tenmilelabs.domain.exception.*
import com.tenmilelabs.domain.service.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(authService: AuthService) {
    route("/auth") {
        post("/register") {
            try {
                val request = call.receive<RegisterRequest>()
                val response = authService.register(request)
                call.respond(HttpStatusCode.Created, response)
            } catch (ex: ValidationException) {
                application.log.warn("Validation error during registration: ${ex.message}")
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(ex.message ?: "Validation failed"))
            } catch (ex: UserAlreadyExistsException) {
                application.log.warn("Duplicate user registration attempt: ${ex.message}")
                call.respond(HttpStatusCode.Conflict, ErrorResponse(ex.message ?: "User already exists"))
            } catch (ex: AuthInternalException) {
                application.log.error("Internal error during registration", ex)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
            } catch (ex: Exception) {
                application.log.error("Unexpected error during registration", ex)
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request format"))
            }
        }

        post("/login") {
            try {
                val request = call.receive<LoginRequest>()
                val response = authService.login(request)
                call.respond(HttpStatusCode.OK, response)
            } catch (ex: ValidationException) {
                application.log.warn("Validation error during login: ${ex.message}")
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(ex.message ?: "Validation failed"))
            } catch (ex: InvalidCredentialsException) {
                application.log.warn("Invalid credentials during login: ${ex.message}")
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ex.message ?: "Invalid credentials"))
            } catch (ex: AuthInternalException) {
                application.log.error("Internal error during login", ex)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
            } catch (ex: Exception) {
                application.log.error("Unexpected error during login", ex)
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request format"))
            }
        }

        post("/refresh") {
            try {
                val request = call.receive<RefreshTokenRequest>()
                val response = authService.refreshToken(request)
                call.respond(HttpStatusCode.OK, response)
            } catch (ex: InvalidRefreshTokenException) {
                application.log.warn("Invalid refresh token: ${ex.message}")
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(ex.message ?: "Invalid or expired refresh token")
                )
            } catch (ex: TokenReuseDetectedException) {
                application.log.error("Token reuse detected: ${ex.message}")
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ex.message ?: "Security breach detected"))
            } catch (ex: UserNotFoundException) {
                application.log.warn("User not found during token refresh: ${ex.message}")
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid refresh token"))
            } catch (ex: AuthInternalException) {
                application.log.error("Internal error during token refresh", ex)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
            } catch (ex: Exception) {
                application.log.error("Unexpected error during token refresh", ex)
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request format"))
            }
        }

        post("/refresh") {
            try {
                val request = call.receive<RefreshTokenRequest>()

                if (request.refreshToken.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Refresh token is required"))
                    return@post
                }

                val response = authService.refreshToken(request)
                if (response != null) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or expired refresh token"))
                }
            } catch (ex: Exception) {
                application.log.error("Error during token refresh", ex)
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request format"))
            }
        }
    }
}
