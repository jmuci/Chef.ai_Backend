package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.domain.exception.AlreadyInHouseholdException
import com.tenmilelabs.domain.exception.InviteNotForCallerException
import com.tenmilelabs.domain.exception.InviteNotFoundException
import com.tenmilelabs.domain.exception.NotHouseholdMemberException
import com.tenmilelabs.domain.model.DepartureOutcome
import com.tenmilelabs.domain.model.Household
import com.tenmilelabs.domain.model.HouseholdInvite
import com.tenmilelabs.domain.model.HouseholdMemberStatus
import com.tenmilelabs.domain.model.HouseholdMembership
import com.tenmilelabs.domain.model.HouseholdRole
import com.tenmilelabs.domain.model.NewHouseholdInvite
import com.tenmilelabs.domain.repository.HouseholdRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * In-memory fake mirroring [com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresHouseholdRepository]'s
 * observable behavior — including its translation of the one-active-household-per-user race into
 * [AlreadyInHouseholdException]. [bumpServerUpdatedAtForHouseholdRows] and [detachPlansOwnedBy]
 * stay no-ops here: this fake has no notion of meal plans at all, so their real effect on
 * `meal_plans`/`grocery_list_item_checks` is exercised via `Postgres*IntegrationTest` instead —
 * for `SyncService` unit tests, see `FakeSyncRepository.seedPlanDetachedFromHousehold`.
 */
class FakeHouseholdRepository : HouseholdRepository {

    private data class HouseholdRow(val id: UUID, var name: String, var ownerId: UUID, var deletedAt: Instant?)

    private val households = mutableMapOf<UUID, HouseholdRow>()
    private val memberships = mutableListOf<HouseholdMembership>()
    private val invites = mutableMapOf<UUID, HouseholdInvite>()
    private val profiles = mutableMapOf<UUID, Pair<String, String>>()

    /** Test helper — controls what a member's displayName/avatarUrl resolve to in [Household.members]. */
    fun seedUserProfile(userId: UUID, displayName: String, avatarUrl: String = "") {
        profiles[userId] = displayName to avatarUrl
    }

    override suspend fun createHousehold(name: String, ownerId: UUID): Household {
        val id = UUID.randomUUID()
        households[id] = HouseholdRow(id, name, ownerId, null)
        insertMembershipOrThrow(id, ownerId, HouseholdRole.OWNER, Clock.System.now())
        return requireNotNull(getHousehold(id))
    }

    override suspend fun getHousehold(id: UUID): Household? {
        val row = households[id]?.takeIf { it.deletedAt == null } ?: return null
        return Household(
            id = row.id,
            name = row.name,
            ownerId = row.ownerId,
            members = memberships.filter { it.householdId == id && it.status == HouseholdMemberStatus.ACTIVE },
        )
    }

    override suspend fun getActiveHouseholdForUser(userId: UUID): Household? {
        val membership = memberships.firstOrNull { it.userId == userId && it.status == HouseholdMemberStatus.ACTIVE }
            ?: return null
        return getHousehold(membership.householdId)
    }

    override suspend fun getActiveMembership(householdId: UUID, userId: UUID): HouseholdMembership? =
        memberships.firstOrNull {
            it.householdId == householdId && it.userId == userId && it.status == HouseholdMemberStatus.ACTIVE
        }

    override suspend fun listActiveMembers(householdId: UUID): List<HouseholdMembership> =
        memberships
            .filter { it.householdId == householdId && it.status == HouseholdMemberStatus.ACTIVE }
            .sortedBy { it.joinedAt }

    override suspend fun renameHousehold(householdId: UUID, name: String, at: Instant) {
        households[householdId]?.name = name
    }

    override suspend fun addMember(
        householdId: UUID,
        userId: UUID,
        role: HouseholdRole,
        at: Instant
    ): HouseholdMembership {
        insertMembershipOrThrow(householdId, userId, role, at)
        return requireNotNull(getActiveMembership(householdId, userId))
    }

    override suspend fun removeMember(householdId: UUID, userId: UUID, at: Instant) {
        val index = memberships.indexOfFirst {
            it.householdId == householdId && it.userId == userId && it.status == HouseholdMemberStatus.ACTIVE
        }
        if (index == -1) return
        memberships[index] = memberships[index].copy(
            status = HouseholdMemberStatus.REMOVED,
            removedAt = at,
            serverRemovedAt = at,
        )
    }

    override suspend fun transferOwnership(householdId: UUID, newOwnerId: UUID, at: Instant) {
        households[householdId]?.ownerId = newOwnerId
        val index = memberships.indexOfFirst {
            it.householdId == householdId && it.userId == newOwnerId && it.status == HouseholdMemberStatus.ACTIVE
        }
        if (index != -1) {
            memberships[index] = memberships[index].copy(role = HouseholdRole.OWNER)
        }
    }

    override suspend fun dissolveHousehold(householdId: UUID, at: Instant) {
        households[householdId]?.deletedAt = at
        for (i in memberships.indices) {
            val m = memberships[i]
            if (m.householdId == householdId && m.status == HouseholdMemberStatus.ACTIVE) {
                memberships[i] = m.copy(status = HouseholdMemberStatus.REMOVED, removedAt = at, serverRemovedAt = at)
            }
        }
    }

    override suspend fun createInvite(invite: NewHouseholdInvite): HouseholdInvite {
        val id = UUID.randomUUID()
        val created = HouseholdInvite(
            id = id,
            householdId = invite.householdId,
            createdBy = invite.createdBy,
            tokenHash = invite.tokenHash,
            inviteeUserId = invite.inviteeUserId,
            inviteeEmail = invite.inviteeEmail,
            singleUse = invite.singleUse,
            maxUses = invite.maxUses,
            useCount = 0,
            expiresAt = invite.expiresAt,
            acceptedBy = null,
            acceptedAt = null,
            revokedAt = null,
            createdAt = Clock.System.now(),
        )
        invites[id] = created
        return created
    }

    override suspend fun findInviteByTokenHash(tokenHash: String): HouseholdInvite? =
        invites.values.firstOrNull { it.tokenHash == tokenHash }

    override suspend fun getInvite(inviteId: UUID): HouseholdInvite? = invites[inviteId]

    override suspend fun listOutstandingInvites(householdId: UUID): List<HouseholdInvite> {
        val now = Clock.System.now()
        return invites.values.filter { it.householdId == householdId && it.isUsable(now) }
    }

    override suspend fun listPendingInvitesForUser(userId: UUID): List<HouseholdInvite> =
        invites.values.filter { it.inviteeUserId == userId && it.revokedAt == null && it.acceptedAt == null }

    override suspend fun revokeInvite(inviteId: UUID, at: Instant) {
        invites[inviteId]?.let { invites[inviteId] = it.copy(revokedAt = at) }
    }

    override suspend fun recordInviteAcceptance(inviteId: UUID, acceptedBy: UUID, at: Instant) {
        val invite = invites[inviteId] ?: return
        invites[inviteId] = invite.copy(
            useCount = invite.useCount + 1,
            acceptedBy = if (invite.singleUse) acceptedBy else invite.acceptedBy,
            acceptedAt = if (invite.singleUse) at else invite.acceptedAt,
        )
    }

    /** No-op — matches [com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresHouseholdRepository]. */
    override suspend fun bumpServerUpdatedAtForHouseholdRows(householdId: UUID, at: Instant) = Unit

    /** No-op — this fake has no notion of meal plans; the real effect (including the
     *  former_household_id/household_detached_at tombstone bookkeeping) is exercised via
     *  Postgres*IntegrationTest and, for SyncService unit tests, FakeSyncRepository's own
     *  seedPlanDetachedFromHousehold. */
    override suspend fun detachPlansOwnedBy(householdId: UUID, userId: UUID, at: Instant) = Unit

    override suspend fun departFromHousehold(householdId: UUID, departingUserId: UUID, at: Instant): DepartureOutcome {
        val departing = getActiveMembership(householdId, departingUserId)
            ?: throw NotHouseholdMemberException(
                "User $departingUserId is not an active member of household $householdId"
            )
        removeMember(householdId, departingUserId, at)
        detachPlansOwnedBy(householdId, departingUserId, at)

        val remaining = listActiveMembers(householdId)
        return when {
            remaining.isEmpty() -> {
                dissolveHousehold(householdId, at)
                DepartureOutcome.HouseholdDissolved
            }
            departing.role == HouseholdRole.OWNER -> {
                val newOwner = remaining.minBy { it.joinedAt }
                transferOwnership(householdId, newOwner.userId, at)
                DepartureOutcome.OwnershipTransferred(newOwner.userId)
            }
            else -> DepartureOutcome.Remained
        }
    }

    override suspend fun acceptInvite(inviteId: UUID, callerId: UUID, at: Instant): Household {
        val invite = invites[inviteId] ?: throw InviteNotFoundException("No invite found for id $inviteId")
        if (!invite.isUsable(at)) {
            throw InviteNotFoundException("Invite ${invite.id} is expired, revoked, or exhausted")
        }
        if (invite.inviteeUserId != null && invite.inviteeUserId != callerId) {
            throw InviteNotForCallerException("Invite ${invite.id} is not addressed to caller $callerId")
        }
        insertMembershipOrThrow(invite.householdId, callerId, HouseholdRole.MEMBER, at)
        recordInviteAcceptance(inviteId, callerId, at)
        bumpServerUpdatedAtForHouseholdRows(invite.householdId, at)
        return requireNotNull(getHousehold(invite.householdId)) {
            "Household ${invite.householdId} not found after join"
        }
    }

    private fun insertMembershipOrThrow(householdId: UUID, userId: UUID, role: HouseholdRole, at: Instant) {
        val alreadyActive = memberships.any { it.userId == userId && it.status == HouseholdMemberStatus.ACTIVE }
        if (alreadyActive) {
            throw AlreadyInHouseholdException("User $userId already belongs to a household")
        }
        val (displayName, avatarUrl) = profiles[userId] ?: ("" to "")
        memberships += HouseholdMembership(
            householdId = householdId,
            userId = userId,
            displayName = displayName,
            avatarUrl = avatarUrl,
            role = role,
            status = HouseholdMemberStatus.ACTIVE,
            joinedAt = at,
            removedAt = null,
            serverRemovedAt = null,
        )
    }

    fun clear() {
        households.clear()
        memberships.clear()
        invites.clear()
        profiles.clear()
    }
}
