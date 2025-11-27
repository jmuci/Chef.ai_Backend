package com.tenmilelabs.infrastructure.auth

import com.tenmilelabs.domain.service.JwtService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun Application.configureJwtAuth(jwtService: JwtService) {
    install(Authentication) {
        jwt("auth-jwt") {
            verifier(jwtService.verifier)

            validate { credential ->
                if (credential.payload.audience.contains(jwtService.audience)) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { defaultScheme, realm ->
                call.respondText(
                    text = "Token is not valid or has expired",
                    status = HttpStatusCode.Unauthorized
                )
            }
        }
    }
}

// Extension to get userId from JWT principal
val ApplicationCall.userId: String?
    get() = principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()

val ApplicationCall.userEmail: String?
    get() = principal<JWTPrincipal>()?.payload?.getClaim("email")?.asString()
