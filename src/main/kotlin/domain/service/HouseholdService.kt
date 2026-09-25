package com.tenmilelabs.domain.service

import com.tenmilelabs.domain.exception.AlreadyInHouseholdException
import com.tenmilelabs.domain.exception.HouseholdNotFoundException
import com.tenmilelabs.domain.exception.HouseholdValidationException
import com.tenmilelabs.domain.exception.InviteNotFoundException
import com.tenmilelabs.domain.exception.InviteNotForCallerException
import com.tenmilelabs.domain.exception.InviteeNotFoundException
import com.tenmilelabs.domain.exception.NotHouseholdMemberException
import com.tenmilelabs.domain.exception.NotHouseholdOwnerException
import com.tenmilelabs.domain.model.DepartureOutcome
import com.tenmilelabs.domain.model.Household
import com.tenmilelabs.domain.model.HouseholdInvite
import com.tenmilelabs.domain.model.HouseholdInvitePreview
import com.tenmilelabs.domain.model.HouseholdMembership
import com.tenmilelabs.domain.model.HouseholdRole
import com.tenmilelabs.domain.model.NewHouseholdInvite
import com.tenmilelabs.domain.repository.HouseholdRepository
import com.tenmilelabs.domain.repository.UserRepository
import com.tenmilelabs.domain.util.TokenHasher
import com.tenmilelabs.domain.util.millisecondPrecisionNow
import io.ktor.util.logging.Logger
import java.util.UUID
import kotlin.time.Duration.Companion.hours

/**
 * Owns every household business rule; routes (added separately) do no more than call into this
 * class and translate [com.tenmilelabs.domain.exception.HouseholdException] subtypes to HTTP
 * status codes. [requireActiveMember] and [requireOwner] are the two choke points every mutating
 * method funnels through — the caller's role is always re-resolved from their own
 * `household_members` row, never trusted from client input.
 */
class HouseholdService(
    private val householdRepository: HouseholdRepository,
    private val userRepository: UserRepository,
    private val log: Logger,
) {

    suspend fun createHousehold(name: String, ownerId: UUID): Household {
        val trimmedName = requireNonBlankName(name)
        if (householdRepository.getActiveHouseholdForUser(ownerId) != null) {
            throw AlreadyInHouseholdException("User $ownerId already belongs to a household")
        }
        val household = householdRepository.createHousehold(trimmedName, ownerId)
        log.info("Created household ${household.id} for owner $ownerId")
        return household
    }

    suspend fun getHouseholdForCaller(callerId: UUID): Household =
        householdRepository.getActiveHouseholdForUser(callerId)
            ?: throw HouseholdNotFoundException("User $callerId has no active household")

    suspend fun renameHousehold(householdId: UUID, callerId: UUID, name: String): Household {
        val trimmedName = requireNonBlankName(name)
        requireOwner(householdId, callerId)
        householdRepository.renameHousehold(householdId, trimmedName, millisecondPrecisionNow())
        return householdRepository.getHousehold(householdId)
            ?: throw HouseholdNotFoundException("Household $householdId not found after rename")
    }

    suspend fun deleteHousehold(householdId: UUID, callerId: UUID) {
        requireOwner(householdId, callerId)
        householdRepository.dissolveHousehold(householdId, millisecondPrecisionNow())
        log.info("Household $householdId deleted by owner $callerId")
    }

    suspend fun listMembers(householdId: UUID, callerId: UUID): List<HouseholdMembership> {
        requireActiveMember(householdId, callerId)
        return householdRepository.listActiveMembers(householdId)
    }

    suspend fun leaveHousehold(householdId: UUID, callerId: UUID) {
        requireActiveMember(householdId, callerId)
        departFromHousehold(householdId, callerId)
    }

    suspend fun removeMember(householdId: UUID, callerId: UUID, targetUserId: UUID) {
        requireOwner(householdId, callerId)
        if (targetUserId == callerId) {
            throw HouseholdValidationException("Use leave instead of removing yourself")
        }
        val target = householdRepository.getActiveMembership(householdId, targetUserId)
            ?: throw NotHouseholdMemberException(
                "User $targetUserId is not an active member of household $householdId"
            )
        if (target.role == HouseholdRole.OWNER) {
            throw HouseholdValidationException(
                "The household owner cannot be removed; transfer ownership or delete the household"
            )
        }
        departFromHousehold(householdId, targetUserId)
    }

    suspend fun createInvite(
        householdId: UUID,
        callerId: UUID,
        inviteeEmail: String?,
        singleUse: Boolean = true,
        maxUses: Int?,
        expiresInHours: Long?,
    ): Pair<HouseholdInvite, String> {
        requireOwner(householdId, callerId)
        if (maxUses != null && maxUses <= 0) {
            throw HouseholdValidationException("maxUses must be positive")
        }
        if (expiresInHours != null && expiresInHours <= 0) {
            throw HouseholdValidationException("expiresInHours must be positive")
        }

        val inviteeUserId = inviteeEmail?.let { email ->
            // Every stored email went through this same sanitization at registration
            // (AuthService.register) before an equality lookup — an unsanitized lookup here would
            // miss a real account whenever the owner types a differently-cased email.
            val sanitizedEmail = InputValidator.sanitizeEmail(email)
            userRepository.findUserByEmail(sanitizedEmail)?.uuid
                ?: throw InviteeNotFoundException("No user found for email $email")
        }

        val rawToken = TokenHasher.generateSecureToken()
        val tokenHash = TokenHasher.sha256Base64(rawToken)
        val now = millisecondPrecisionNow()
        val boundedHours = (expiresInHours?.coerceIn(1L, MAX_INVITE_EXPIRY_HOURS)) ?: DEFAULT_INVITE_EXPIRY_HOURS
        val expiresAt = now.plus(boundedHours.hours)

        val invite = householdRepository.createInvite(
            NewHouseholdInvite(
                householdId = householdId,
                createdBy = callerId,
                tokenHash = tokenHash,
                inviteeUserId = inviteeUserId,
                inviteeEmail = inviteeEmail,
                singleUse = singleUse,
                maxUses = maxUses,
                expiresAt = expiresAt,
            )
        )
        log.info("Created invite ${invite.id} for household $householdId")
        return invite to rawToken
    }

    suspend fun listOutstandingInvites(householdId: UUID, callerId: UUID): List<HouseholdInvite> {
        requireOwner(householdId, callerId)
        return householdRepository.listOutstandingInvites(householdId)
    }

    suspend fun listPendingInvitesForCaller(callerId: UUID): List<HouseholdInvite> =
        householdRepository.listPendingInvitesForUser(callerId)

    suspend fun revokeInvite(householdId: UUID, callerId: UUID, inviteId: UUID) {
        requireOwner(householdId, callerId)
        val invite = requireInvite(inviteId)
        if (invite.householdId != householdId) {
            throw InviteNotFoundException("Invite $inviteId not found")
        }
        householdRepository.revokeInvite(inviteId, millisecondPrecisionNow())
    }

    /**
     * Shares `revoked_at` with owner-initiated revocation rather than a separate `declined_at`
     * column — the two are distinguished by who initiated them (caller is the invitee here, the
     * household owner for [revokeInvite]), which the audit trail can already tell apart via the
     * invite's `invitee_user_id`. Documented in `docs/household-architecture.md`.
     */
    suspend fun declineInvite(inviteId: UUID, callerId: UUID) {
        val invite = requireInvite(inviteId)
        if (invite.inviteeUserId != callerId) {
            throw InviteNotForCallerException("Invite $inviteId is not addressed to caller $callerId")
        }
        householdRepository.revokeInvite(inviteId, millisecondPrecisionNow())
    }

    /**
     * Unauthenticated-friendly preview of what a token invites the caller into — just enough for a
     * signed-out link tap to show "Join Jose's household?" before forcing sign-in. Applies the same
     * [HouseholdInvite.isUsable] check as accepting: an expired/revoked/exhausted invite previews
     * the same as a nonexistent one, for the same enumeration-resistance reason.
     */
    suspend fun previewInvite(token: String): HouseholdInvitePreview {
        val invite = householdRepository.findInviteByTokenHash(TokenHasher.sha256Base64(token))
            ?: throw InviteNotFoundException("No invite found for the given token")
        if (!invite.isUsable(millisecondPrecisionNow())) {
            throw InviteNotFoundException("No invite found for the given token")
        }
        val household = householdRepository.getHousehold(invite.householdId)
            ?: throw InviteNotFoundException("No invite found for the given token")
        val inviter = userRepository.findUserById(invite.createdBy)
        return HouseholdInvitePreview(
            householdName = household.name,
            inviterDisplayName = inviter?.displayName ?: "",
        )
    }

    suspend fun joinByToken(token: String, callerId: UUID): Household {
        val invite = householdRepository.findInviteByTokenHash(TokenHasher.sha256Base64(token))
            ?: throw InviteNotFoundException("No invite found for the given token")
        return acceptInvite(invite.id, callerId)
    }

    /**
     * The in-app inbox path. Unlike [joinByToken] it presents no secret — only the invite's id — so
     * it is restricted to invites addressed to the caller, which is all the pending-invites inbox
     * ever lists. An open (link-style) invite must be redeemed with its token: its id is not a
     * secret (it's logged, and returned to the owner by `GET /{id}/invites`), and accepting one by
     * id would let anyone who learned it join without ever holding the link.
     */
    suspend fun acceptInviteById(inviteId: UUID, callerId: UUID): Household {
        val invite = requireInvite(inviteId)
        if (invite.inviteeUserId != callerId) {
            throw InviteNotForCallerException("Invite $inviteId is not addressed to caller $callerId")
        }
        return acceptInvite(inviteId, callerId)
    }

    /**
     * Shared core for the token path ([joinByToken]) and the in-app path ([acceptInviteById]).
     * Delegates validity (not found/expired/revoked/exhausted), invitee-mismatch, membership
     * insert, invite-consumption, and cursor backfill entirely to
     * [HouseholdRepository.acceptInvite], which does all of it atomically under a lock on the
     * invite row — see its KDoc for why a per-call-transaction sequence here previously left a
     * race where two different callers could both accept the same single-use invite.
     */
    private suspend fun acceptInvite(inviteId: UUID, callerId: UUID): Household {
        val household = householdRepository.acceptInvite(inviteId, callerId, millisecondPrecisionNow())
        log.info("User $callerId joined household ${household.id} via invite $inviteId")
        return household
    }

    /**
     * Shared "leave or remove" core (backend prompt §4): delegates the whole mark-REMOVED /
     * detach-plans / promote-or-dissolve sequence to [HouseholdRepository.departFromHousehold],
     * which runs it atomically under a lock on the household row — see its KDoc for why a
     * per-call-transaction sequence here previously left a race where two members leaving at once
     * could leave `households.owner_id` pointing at a member who was just removed.
     */
    private suspend fun departFromHousehold(householdId: UUID, departingUserId: UUID) {
        val now = millisecondPrecisionNow()
        when (val outcome = householdRepository.departFromHousehold(householdId, departingUserId, now)) {
            DepartureOutcome.HouseholdDissolved ->
                log.info("Dissolved household $householdId — last member $departingUserId left")
            is DepartureOutcome.OwnershipTransferred ->
                log.info("Transferred ownership of household $householdId to ${outcome.newOwnerId}")
            DepartureOutcome.Remained -> Unit
        }
    }

    private suspend fun requireInvite(inviteId: UUID): HouseholdInvite =
        householdRepository.getInvite(inviteId) ?: throw InviteNotFoundException("Invite $inviteId not found")

    private fun requireNonBlankName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw HouseholdValidationException("Household name cannot be blank")
        return trimmed
    }

    private suspend fun requireActiveMember(householdId: UUID, callerId: UUID): HouseholdMembership =
        householdRepository.getActiveMembership(householdId, callerId)
            ?: throw NotHouseholdMemberException("User $callerId is not an active member of household $householdId")

    private suspend fun requireOwner(householdId: UUID, callerId: UUID): HouseholdMembership {
        val membership = requireActiveMember(householdId, callerId)
        if (membership.role != HouseholdRole.OWNER) {
            throw NotHouseholdOwnerException("User $callerId is not the owner of household $householdId")
        }
        return membership
    }

    private companion object {
        const val DEFAULT_INVITE_EXPIRY_HOURS = 24L * 7
        const val MAX_INVITE_EXPIRY_HOURS = 24L * 30
    }
}
