package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.application.dto.LoginRequest
import com.tenmilelabs.application.dto.RegisterRequest
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

                // Basic validation
                if (request.email.isBlank() || request.username.isBlank() || request.password.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("All fields are required"))
                    return@post
                }

                val response = authService.register(request)
                if (response != null) {
                    call.respond(HttpStatusCode.Created, response)
                } else {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Registration failed. Email may already be in use or password requirements not met.")
                    )
                }
            } catch (ex: Exception) {
                application.log.error("Error during registration", ex)
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request format"))
            }
        }

        post("/login") {
            try {
                val request = call.receive<LoginRequest>()

                if (request.email.isBlank() || request.password.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Email and password are required"))
                    return@post
                }

                val response = authService.login(request)
                if (response != null) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid credentials"))
                }
            } catch (ex: Exception) {
                application.log.error("Error during login", ex)
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request format"))
            }
        }
    }
}
