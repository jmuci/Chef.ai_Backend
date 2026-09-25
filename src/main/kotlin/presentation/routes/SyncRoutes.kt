package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.application.dto.SyncPushRequest
import com.tenmilelabs.domain.service.SyncService
import com.tenmilelabs.infrastructure.auth.userId
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.plugins.BadRequestException
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

            val traceId = UUID.randomUUID().toString()
            try {
                val request = call.receive<SyncPushRequest>()
                val response = syncService.pushRecipes(userId, request)
                call.respond(HttpStatusCode.OK, response)
            } catch (ex: BadRequestException) {
                // What call.receive<SyncPushRequest>() actually throws on a conversion failure -
                // Ktor's ContentNegotiation wraps the underlying JsonConvertException/
                // SerializationException in this type, so those two catches below never fire for
                // a malformed body; they're kept for any exception thrown directly by future code
                // in this block. Without this clause, every malformed/incomplete client payload
                // fell through to the generic catch (ex: Exception) below and answered 500.
                call.application.environment.log.warn("Sync push request conversion failed [traceId=$traceId]", ex)
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(errorWithTrace("Malformed sync push payload", traceId))
                )
            } catch (ex: JsonConvertException) {
                call.application.environment.log.warn("Sync push JSON conversion failed [traceId=$traceId]", ex)
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(errorWithTrace("Malformed sync push payload", traceId))
                )
            } catch (ex: SerializationException) {
                call.application.environment.log.warn("Sync push serialization failed [traceId=$traceId]", ex)
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(errorWithTrace("Malformed sync push payload", traceId))
                )
            } catch (ex: IllegalArgumentException) {
                call.application.environment.log.warn("Sync push validation failed [traceId=$traceId]", ex)
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(errorWithTrace("Invalid sync push payload", traceId))
                )
            } catch (ex: ExposedSQLException) {
                call.application.environment.log.error("Sync push database constraint failure [traceId=$traceId]", ex)
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse(errorWithTrace("Sync push database conflict", traceId))
                )
            } catch (ex: SQLException) {
                call.application.environment.log.error("Sync push SQL failure [traceId=$traceId]", ex)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(errorWithTrace("Sync push database error", traceId))
                )
            } catch (ex: Exception) {
                call.application.environment.log.error("Sync push unexpected failure [traceId=$traceId]", ex)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(errorWithTrace("Sync push failed unexpectedly", traceId))
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
            if (since < 0) {
                // Checked here, not left to pullRecipes' require(): that IllegalArgumentException
                // would land in the generic catch below and answer 500.
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("since must be non-negative"))
                return@get
            }

            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            if (limit <= 0) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("limit must be greater than 0"))
                return@get
            }

            val traceId = UUID.randomUUID().toString()
            try {
                val response = syncService.pullRecipes(userId, since, limit)
                call.respond(HttpStatusCode.OK, response)
            } catch (ex: ExposedSQLException) {
                call.application.environment.log.error("Sync pull database failure [traceId=$traceId]", ex)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(errorWithTrace("Sync pull database error", traceId))
                )
            } catch (ex: SQLException) {
                call.application.environment.log.error("Sync pull SQL failure [traceId=$traceId]", ex)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(errorWithTrace("Sync pull database error", traceId))
                )
            } catch (ex: Exception) {
                call.application.environment.log.error("Sync pull unexpected failure [traceId=$traceId]", ex)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(errorWithTrace("Sync pull failed unexpectedly", traceId))
                )
            }
        }
    }
}

private fun parseUserId(rawUserId: String?): UUID? =
    try {
        rawUserId?.let(UUID::fromString)
    } catch (_: IllegalArgumentException) {
        null
    }

/**
 * Client-facing error text: a stable message plus a correlation id, never the exception itself.
 *
 * This used to append the exception type and 300 characters of its message. For the
 * ExposedSQLException/SQLException branches below that handed the caller constraint names, column
 * names and SQL fragments - a free schema map, and what the TODO above was about. Callers get the
 * traceId; the detail goes to the log under the same id.
 */
private fun errorWithTrace(message: String, traceId: String): String = "$message (traceId=$traceId)"
