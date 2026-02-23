package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.application.dto.SyncPushRequest
import com.tenmilelabs.domain.service.SyncService
import com.tenmilelabs.infrastructure.auth.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.syncRoutes(syncService: SyncService) {
    route("/sync") {
        post("/push") {
            val userId = parseUserId(call.userId) ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("User not authenticated"))
                return@post
            }

            try {
                val request = call.receive<SyncPushRequest>()
                val response = syncService.pushRecipes(userId, request)
                call.respond(HttpStatusCode.OK, response)
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Malformed sync push payload"))
            }
        }

        get("/pull") {
            val userId = parseUserId(call.userId) ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("User not authenticated"))
                return@get
            }

            val since = call.request.queryParameters["since"]?.toLongOrNull()
            if (since == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("since query parameter is required"))
                return@get
            }

            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            if (limit <= 0) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("limit must be greater than 0"))
                return@get
            }

            val response = syncService.pullRecipes(userId, since, limit)
            call.respond(HttpStatusCode.OK, response)
        }
    }
}

private fun parseUserId(rawUserId: String?): UUID? =
    try {
        rawUserId?.let(UUID::fromString)
    } catch (_: IllegalArgumentException) {
        null
    }
