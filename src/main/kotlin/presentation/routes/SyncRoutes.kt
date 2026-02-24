package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.application.dto.SyncPushRequest
import com.tenmilelabs.domain.service.SyncService
import com.tenmilelabs.infrastructure.auth.userId
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.SerializationException
import org.jetbrains.exposed.exceptions.ExposedSQLException
import java.sql.SQLException
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
            } catch (ex: JsonConvertException) {
                call.application.environment.log.warn("Sync push JSON conversion failed: ${ex.message}")
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(buildDetailedError("Malformed sync push payload", ex))
                )
            } catch (ex: SerializationException) {
                call.application.environment.log.warn("Sync push serialization failed: ${ex.message}")
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(buildDetailedError("Malformed sync push payload", ex))
                )
            } catch (ex: IllegalArgumentException) {
                call.application.environment.log.warn("Sync push validation failed: ${ex.message}")
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(buildDetailedError("Invalid sync push payload", ex))
                )
            } catch (ex: ExposedSQLException) {
                call.application.environment.log.error("Sync push database constraint failure", ex)
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse(buildDetailedError("Sync push database conflict", ex))
                )
            } catch (ex: SQLException) {
                call.application.environment.log.error("Sync push SQL failure", ex)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(buildDetailedError("Sync push database error", ex))
                )
            } catch (ex: Exception) {
                call.application.environment.log.error("Sync push unexpected failure", ex)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(buildDetailedError("Sync push failed unexpectedly", ex))
                )
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

private fun buildDetailedError(prefix: String, ex: Throwable): String {
    val type = ex::class.simpleName ?: ex::class.java.name
    val detail = ex.message?.trim()?.take(300) ?: "no details"
    return "$prefix [$type]: $detail"
}
