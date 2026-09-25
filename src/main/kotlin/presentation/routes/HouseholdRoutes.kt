package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.CreateHouseholdRequest
import com.tenmilelabs.application.dto.CreateInviteRequest
import com.tenmilelabs.application.dto.CreateInviteResponse
import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.application.dto.JoinHouseholdRequest
import com.tenmilelabs.application.dto.RenameHouseholdRequest
import com.tenmilelabs.application.dto.toResponse
import com.tenmilelabs.application.dto.toSummaryResponse
import com.tenmilelabs.domain.exception.AlreadyInHouseholdException
import com.tenmilelabs.domain.exception.HouseholdException
import com.tenmilelabs.domain.exception.HouseholdInternalException
import com.tenmilelabs.domain.exception.HouseholdNotFoundException
import com.tenmilelabs.domain.exception.HouseholdValidationException
import com.tenmilelabs.domain.exception.InviteNotFoundException
import com.tenmilelabs.domain.exception.InviteNotForCallerException
import com.tenmilelabs.domain.exception.InviteeNotFoundException
import com.tenmilelabs.domain.exception.NotHouseholdMemberException
import com.tenmilelabs.domain.exception.NotHouseholdOwnerException
import com.tenmilelabs.domain.service.HouseholdService
import com.tenmilelabs.infrastructure.auth.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import java.util.UUID

val HOUSEHOLD_INVITE_CREATE_RATE_LIMIT_NAME = RateLimitName("household-invite-create")
val HOUSEHOLD_JOIN_RATE_LIMIT_NAME = RateLimitName("household-join")
val HOUSEHOLD_INVITE_PREVIEW_RATE_LIMIT_NAME = RateLimitName("household-invite-preview")

/**
 * `GET /api/v1/households/invites/preview` — the one household endpoint that isn't fully
 * authenticated. Mounted under `authenticate("auth-jwt", optional = true)` in Routing.kt so a
 * link opened while signed out still gets a 401 for a garbled token (not silently ignored) while
 * a missing Authorization header is never challenged. The response itself doesn't depend on
 * caller identity — [HouseholdService.previewInvite] doesn't need a userId at all — so
 * `optional = true` here buys nothing but that one distinction, which matches the surrounding
 * anonymous-capable routes' semantics rather than inventing a third auth tier.
 */
fun Route.householdPreviewRoutes(householdService: HouseholdService) {
    rateLimit(HOUSEHOLD_INVITE_PREVIEW_RATE_LIMIT_NAME) {
        get("/api/v1/households/invites/preview") {
            val token = call.request.queryParameters["token"]
            if (token.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("token query parameter is required"))
                return@get
            }
            call.handleHousehold {
                respond(HttpStatusCode.OK, householdService.previewInvite(token).toResponse())
            }
        }
    }
}

/**
 * Every other household endpoint (backend prompt §5) — all require a known caller, mounted under
 * `authenticate("auth-jwt")` in Routing.kt. Business rules all live in [HouseholdService]; this
 * file does no more than parse/validate the request shape and translate the thrown
 * [HouseholdException] into an HTTP status via [respondHouseholdException].
 */
fun Route.householdRoutes(householdService: HouseholdService, inviteBaseUrl: String) {
    route("/api/v1/households") {
        post {
            val callerId = call.requireUserId() ?: return@post
            val request = call.receiveOrRespondBadRequest<CreateHouseholdRequest>("name is required") ?: return@post
            call.handleHousehold {
                respond(HttpStatusCode.Created, householdService.createHousehold(request.name, callerId).toResponse())
            }
        }

        get("/me") {
            val callerId = call.requireUserId() ?: return@get
            call.handleHousehold {
                respond(HttpStatusCode.OK, householdService.getHouseholdForCaller(callerId).toResponse())
            }
        }

        rateLimit(HOUSEHOLD_JOIN_RATE_LIMIT_NAME) {
            post("/join") {
                val callerId = call.requireUserId() ?: return@post
                val request = call.receiveOrRespondBadRequest<JoinHouseholdRequest>("token is required") ?: return@post
                if (request.token.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("token must not be blank"))
                    return@post
                }
                call.handleHousehold {
                    respond(HttpStatusCode.OK, householdService.joinByToken(request.token, callerId).toResponse())
                }
            }
        }

        route("/invites") {
            get("/pending") {
                val callerId = call.requireUserId() ?: return@get
                val invites = householdService.listPendingInvitesForCaller(callerId)
                call.respond(HttpStatusCode.OK, invites.map { it.toSummaryResponse() })
            }

            route("/{inviteId}") {
                post("/accept") {
                    val callerId = call.requireUserId() ?: return@post
                    val inviteId = call.requireUuidParam("inviteId") ?: return@post
                    call.handleHousehold {
                        respond(HttpStatusCode.OK, householdService.acceptInviteById(inviteId, callerId).toResponse())
                    }
                }

                post("/decline") {
                    val callerId = call.requireUserId() ?: return@post
                    val inviteId = call.requireUuidParam("inviteId") ?: return@post
                    call.handleHousehold {
                        householdService.declineInvite(inviteId, callerId)
                        respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }

        route("/{id}") {
            patch {
                val callerId = call.requireUserId() ?: return@patch
                val householdId = call.requireUuidParam("id") ?: return@patch
                val request = call.receiveOrRespondBadRequest<RenameHouseholdRequest>("name is required")
                    ?: return@patch
                call.handleHousehold {
                    val household = householdService.renameHousehold(householdId, callerId, request.name)
                    respond(HttpStatusCode.OK, household.toResponse())
                }
            }

            delete {
                val callerId = call.requireUserId() ?: return@delete
                val householdId = call.requireUuidParam("id") ?: return@delete
                call.handleHousehold {
                    householdService.deleteHousehold(householdId, callerId)
                    respond(HttpStatusCode.NoContent)
                }
            }

            get("/members") {
                val callerId = call.requireUserId() ?: return@get
                val householdId = call.requireUuidParam("id") ?: return@get
                call.handleHousehold {
                    val members = householdService.listMembers(householdId, callerId)
                    respond(HttpStatusCode.OK, members.map { it.toResponse() })
                }
            }

            delete("/members/{userId}") {
                val callerId = call.requireUserId() ?: return@delete
                val householdId = call.requireUuidParam("id") ?: return@delete
                val targetUserId = call.requireUuidParam("userId") ?: return@delete
                call.handleHousehold {
                    householdService.removeMember(householdId, callerId, targetUserId)
                    respond(HttpStatusCode.NoContent)
                }
            }

            post("/members/me/leave") {
                val callerId = call.requireUserId() ?: return@post
                val householdId = call.requireUuidParam("id") ?: return@post
                call.handleHousehold {
                    householdService.leaveHousehold(householdId, callerId)
                    respond(HttpStatusCode.NoContent)
                }
            }

            rateLimit(HOUSEHOLD_INVITE_CREATE_RATE_LIMIT_NAME) {
                post("/invites") {
                    val callerId = call.requireUserId() ?: return@post
                    val householdId = call.requireUuidParam("id") ?: return@post
                    // Every field is optional on the wire (see CreateInviteRequest defaults), so a
                    // body-less POST is treated the same as `{}`. A body that is present but
                    // doesn't parse is rejected: silently substituting the defaults turned e.g. an
                    // intended email-restricted invite with one mistyped field into an open,
                    // unrestricted invite link.
                    val rawBody = call.receiveText()
                    val request = if (rawBody.isBlank()) {
                        CreateInviteRequest()
                    } else {
                        try {
                            inviteRequestJson.decodeFromString<CreateInviteRequest>(rawBody)
                        } catch (_: IllegalArgumentException) {
                            // SerializationException is an IllegalArgumentException subtype.
                            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Malformed invite request body"))
                            return@post
                        }
                    }
                    call.handleHousehold {
                        val (invite, rawToken) = householdService.createInvite(
                            householdId = householdId,
                            callerId = callerId,
                            inviteeEmail = request.inviteeEmail,
                            singleUse = request.singleUse,
                            maxUses = request.maxUses,
                            expiresInHours = request.expiresInHours,
                        )
                        respond(
                            HttpStatusCode.Created,
                            CreateInviteResponse(
                                token = rawToken,
                                url = "$inviteBaseUrl?token=$rawToken",
                                expiresAt = invite.expiresAt.toEpochMilliseconds(),
                                singleUse = invite.singleUse,
                                maxUses = invite.maxUses,
                            )
                        )
                    }
                }
            }

            get("/invites") {
                val callerId = call.requireUserId() ?: return@get
                val householdId = call.requireUuidParam("id") ?: return@get
                call.handleHousehold {
                    val invites = householdService.listOutstandingInvites(householdId, callerId)
                    respond(HttpStatusCode.OK, invites.map { it.toSummaryResponse() })
                }
            }

            delete("/invites/{inviteId}") {
                val callerId = call.requireUserId() ?: return@delete
                val householdId = call.requireUuidParam("id") ?: return@delete
                val inviteId = call.requireUuidParam("inviteId") ?: return@delete
                call.handleHousehold {
                    householdService.revokeInvite(householdId, callerId, inviteId)
                    respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

/** Same shape as every other route file's UUID path-param idiom (see MealPlanRoutes.kt). */
private suspend fun RoutingCall.requireUuidParam(name: String): UUID? {
    val parsed = try {
        parameters[name]?.let(UUID::fromString)
    } catch (_: IllegalArgumentException) {
        null
    }
    if (parsed == null) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("$name path parameter is not a valid UUID"))
    }
    return parsed
}

/** Every route in this file requires auth, so a missing/malformed `userId` claim is always a 401. */
private suspend fun RoutingCall.requireUserId(): UUID? {
    val parsed = try {
        userId?.let(UUID::fromString)
    } catch (_: IllegalArgumentException) {
        null
    }
    if (parsed == null) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("User not authenticated"))
    }
    return parsed
}

/** Same decoding rules as the ContentNegotiation config in Routing.kt. */
private val inviteRequestJson = Json { ignoreUnknownKeys = true }

private suspend inline fun <reified T : Any> RoutingCall.receiveOrRespondBadRequest(message: String): T? =
    try {
        receive<T>()
    } catch (_: Exception) {
        respond(HttpStatusCode.BadRequest, ErrorResponse(message))
        null
    }

/**
 * Runs [block] and translates any thrown [HouseholdException] via [respondHouseholdException] —
 * every handler below does exactly this, so the try/catch lives here once instead of at all 15
 * call sites (where a handler forgetting it wouldn't be caught by the compiler, unlike the
 * exhaustive `when` in [respondHouseholdException]).
 */
private suspend fun RoutingCall.handleHousehold(block: suspend RoutingCall.() -> Unit) {
    try {
        block()
    } catch (ex: HouseholdException) {
        respondHouseholdException(ex)
    }
}

/**
 * Central [HouseholdException] → HTTP status mapping. A `when` over a sealed class rather than one
 * `catch` clause per subtype (the pattern AuthRoutes.kt uses) — with 15 endpoints sharing this
 * hierarchy, repeating an 8-clause catch chain at every call site would dwarf the actual route
 * logic. The compiler still enforces exhaustiveness: a new [HouseholdException] subtype fails this
 * `when` to compile until it's mapped here.
 */
private suspend fun RoutingCall.respondHouseholdException(ex: HouseholdException) {
    val status = when (ex) {
        is HouseholdNotFoundException -> HttpStatusCode.NotFound
        is NotHouseholdOwnerException -> HttpStatusCode.Forbidden
        is NotHouseholdMemberException -> HttpStatusCode.Forbidden
        is AlreadyInHouseholdException -> HttpStatusCode.Conflict
        is HouseholdValidationException -> HttpStatusCode.BadRequest
        is InviteNotFoundException -> HttpStatusCode.NotFound
        is InviteNotForCallerException -> HttpStatusCode.Forbidden
        is InviteeNotFoundException -> HttpStatusCode.NotFound
        is HouseholdInternalException -> HttpStatusCode.InternalServerError
    }
    respond(status, ErrorResponse(ex.message ?: "Household operation failed"))
}
