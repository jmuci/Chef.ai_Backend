package com.tenmilelabs.domain.service

import com.tenmilelabs.domain.exception.AlreadyInHouseholdException
import com.tenmilelabs.domain.exception.HouseholdNotFoundException
import com.tenmilelabs.domain.exception.HouseholdValidationException
import com.tenmilelabs.domain.exception.InviteNotFoundException
import com.tenmilelabs.domain.exception.InviteNotForCallerException
import com.tenmilelabs.domain.exception.InviteeNotFoundException
import com.tenmilelabs.domain.exception.NotHouseholdMemberException
import com.tenmilelabs.domain.exception.NotHouseholdOwnerException
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
            userRepository.findUserByEmail(email)?.uuid
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
        return acceptInvite(invite, callerId)
    }

    suspend fun acceptInviteById(inviteId: UUID, callerId: UUID): Household =
        acceptInvite(requireInvite(inviteId), callerId)

    /**
     * Shared core for the token path ([joinByToken]) and the in-app path ([acceptInviteById]).
     * Order matters: validity (not found/expired/revoked/exhausted) is checked uniformly via
     * [HouseholdInvite.isUsable] before the invitee-mismatch check, so a caller probing invite ids
     * can't distinguish "doesn't exist" from "exists but isn't yours" from "exists but expired."
     *
     * The membership insert happens *before* [HouseholdRepository.recordInviteAcceptance] bumps
     * `use_count` — a losing racer against the one-household-per-user unique index rolls the whole
     * transaction back before it ever consumes a reusable invite's budget, so no separate
     * exhaustion bookkeeping is needed for that race.
     */
    private suspend fun acceptInvite(invite: HouseholdInvite, callerId: UUID): Household {
        val now = millisecondPrecisionNow()
        if (!invite.isUsable(now)) {
            throw InviteNotFoundException("Invite ${invite.id} is expired, revoked, or exhausted")
        }
        if (invite.inviteeUserId != null && invite.inviteeUserId != callerId) {
            throw InviteNotForCallerException("Invite ${invite.id} is not addressed to caller $callerId")
        }

        householdRepository.addMember(invite.householdId, callerId, HouseholdRole.MEMBER, now)
        householdRepository.recordInviteAcceptance(invite.id, callerId, now)
        householdRepository.bumpServerUpdatedAtForHouseholdRows(invite.householdId, now)

        log.info("User $callerId joined household ${invite.householdId} via invite ${invite.id}")
        return householdRepository.getHousehold(invite.householdId)
            ?: throw HouseholdNotFoundException("Household ${invite.householdId} not found after join")
    }

    /**
     * Shared "leave or remove" core (backend prompt §4): mark the departing membership REMOVED,
     * detach their own plans, then either promote the earliest-joined remaining member (if the
     * departing member was OWNER and others remain) or dissolve the household (if none remain).
     * The two outcomes are mutually exclusive by construction, so checking emptiness first is
     * only for readability, not correctness.
     */
    private suspend fun departFromHousehold(householdId: UUID, departingUserId: UUID) {
        val departing = householdRepository.getActiveMembership(householdId, departingUserId)
            ?: throw NotHouseholdMemberException(
                "User $departingUserId is not an active member of household $householdId"
            )
        val now = millisecondPrecisionNow()

        householdRepository.removeMember(householdId, departingUserId, now)
        householdRepository.detachPlansOwnedBy(householdId, departingUserId)

        val remaining = householdRepository.listActiveMembers(householdId)
        when {
            remaining.isEmpty() -> {
                householdRepository.dissolveHousehold(householdId, now)
                log.info("Dissolved household $householdId — last member $departingUserId left")
            }
            departing.role == HouseholdRole.OWNER -> {
                val newOwner = remaining.minBy { it.joinedAt }
                householdRepository.transferOwnership(householdId, newOwner.userId, now)
                log.info("Transferred ownership of household $householdId to ${newOwner.userId}")
            }
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
