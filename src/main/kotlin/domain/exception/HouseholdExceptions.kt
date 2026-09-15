package com.tenmilelabs.domain.exception

/**
 * Base exception for all household-related errors. Mirrors [AuthException]'s shape: one sealed
 * hierarchy, one subtype per failure the caller needs to distinguish, mapped 1:1 to an HTTP status
 * by the routes layer (added in a later PR — this type exists without any HTTP surface yet).
 */
sealed class HouseholdException(message: String) : Exception(message)

/** Thrown when a household id does not resolve to an existing, non-deleted household. */
class HouseholdNotFoundException(message: String) : HouseholdException(message)

/** Thrown when a caller who is not the household's OWNER attempts an owner-only action. */
class NotHouseholdOwnerException(message: String) : HouseholdException(message)

/** Thrown when a caller has no ACTIVE membership in the household they're acting against. */
class NotHouseholdMemberException(message: String) : HouseholdException(message)

/**
 * Thrown for household-specific input validation failures (blank name, removing yourself via the
 * owner-remove path instead of leave, removing the owner). Deliberately not the `AuthException`
 * hierarchy's [ValidationException] — that type belongs to auth flows, and reusing it here would
 * make a route's exception-to-status mapping ambiguous about which domain actually failed.
 */
class HouseholdValidationException(message: String) : HouseholdException(message)

/**
 * Thrown when a caller who already has an ACTIVE household membership tries to create or join
 * another one. A user belongs to at most one household — see the partial unique index on
 * `household_members(user_id)` for the DB-level backstop this exception surfaces.
 */
class AlreadyInHouseholdException(message: String) : HouseholdException(message)

/**
 * Thrown for a bad, expired, revoked, or use-exhausted invite. Deliberately used for all four
 * cases uniformly (never a distinct exception per reason) so the route layer can't leak which one
 * applied — see the invite-accept enumeration-resistance note in the backend prompt.
 */
class InviteNotFoundException(message: String) : HouseholdException(message)

/** Thrown when a caller accepts an invite whose `invitee_user_id` names a different account. */
class InviteNotForCallerException(message: String) : HouseholdException(message)

/** Thrown when an email-addressed invite is created for an email with no matching account. */
class InviteeNotFoundException(message: String) : HouseholdException(message)

/** Thrown when an underlying operation fails unexpectedly (e.g. database errors). */
class HouseholdInternalException(message: String, cause: Throwable? = null) : HouseholdException(message) {
    init {
        if (cause != null) {
            initCause(cause)
        }
    }
}
