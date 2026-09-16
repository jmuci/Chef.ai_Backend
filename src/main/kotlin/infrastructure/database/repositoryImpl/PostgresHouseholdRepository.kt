package com.tenmilelabs.infrastructure.database.repositoryImpl

import com.tenmilelabs.domain.exception.AlreadyInHouseholdException
import com.tenmilelabs.domain.model.Household
import com.tenmilelabs.domain.model.HouseholdInvite
import com.tenmilelabs.domain.model.HouseholdMemberStatus
import com.tenmilelabs.domain.model.HouseholdMembership
import com.tenmilelabs.domain.model.HouseholdRole
import com.tenmilelabs.domain.model.NewHouseholdInvite
import com.tenmilelabs.domain.repository.HouseholdRepository
import com.tenmilelabs.infrastructure.database.mappers.suspendTransaction
import com.tenmilelabs.infrastructure.database.tables.HouseholdInviteTable
import com.tenmilelabs.infrastructure.database.tables.HouseholdMemberTable
import com.tenmilelabs.infrastructure.database.tables.HouseholdTable
import com.tenmilelabs.infrastructure.database.tables.UserTable
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

/**
 * The partial unique index enforcing "one ACTIVE household per user" lives on
 * `household_members` (see [com.tenmilelabs.infrastructure.database.createHouseholdConstraintsIfMissing]),
 * not on this class. [insertMembership] relies on Postgres raising a unique-violation on that index
 * and translates it into [AlreadyInHouseholdException] — see [addMember].
 */
class PostgresHouseholdRepository : HouseholdRepository {

    override suspend fun createHousehold(name: String, ownerId: UUID): Household = suspendTransaction {
        val householdId = UUID.randomUUID()
        HouseholdTable.insert {
            it[id] = EntityID(householdId, HouseholdTable)
            it[HouseholdTable.name] = name
            it[owner_id] = EntityID(ownerId, UserTable)
        }
        insertMembershipOrThrow(householdId, ownerId, HouseholdRole.OWNER, at = null)
        requireNotNull(loadHousehold(householdId)) { "Just-created household $householdId vanished" }
    }

    override suspend fun getHousehold(id: UUID): Household? = suspendTransaction { loadHousehold(id) }

    override suspend fun getActiveHouseholdForUser(userId: UUID): Household? = suspendTransaction {
        val householdId = HouseholdMemberTable
            .selectAll()
            .where {
                (HouseholdMemberTable.user_id eq EntityID(userId, UserTable)) and
                    (HouseholdMemberTable.status eq HouseholdMemberStatus.ACTIVE.name)
            }
            .firstOrNull()
            ?.get(HouseholdMemberTable.household_id)
            ?.value
            ?: return@suspendTransaction null
        loadHousehold(householdId)
    }

    override suspend fun getActiveMembership(householdId: UUID, userId: UUID): HouseholdMembership? =
        suspendTransaction {
            activeMembershipRow(householdId, userId)?.let { listOf(it).toHouseholdMemberships().first() }
        }

    override suspend fun listActiveMembers(householdId: UUID): List<HouseholdMembership> = suspendTransaction {
        HouseholdMemberTable
            .selectAll()
            .where {
                (HouseholdMemberTable.household_id eq EntityID(householdId, HouseholdTable)) and
                    (HouseholdMemberTable.status eq HouseholdMemberStatus.ACTIVE.name)
            }
            .orderBy(HouseholdMemberTable.joined_at, SortOrder.ASC)
            .toList()
            .toHouseholdMemberships()
    }

    override suspend fun renameHousehold(householdId: UUID, name: String, at: Instant): Unit = suspendTransaction {
        HouseholdTable.update({ HouseholdTable.id eq EntityID(householdId, HouseholdTable) }) {
            it[HouseholdTable.name] = name
            it[updated_at] = at
        }
    }

    override suspend fun addMember(
        householdId: UUID,
        userId: UUID,
        role: HouseholdRole,
        at: Instant
    ): HouseholdMembership = suspendTransaction {
        insertMembershipOrThrow(householdId, userId, role, at)
        requireNotNull(activeMembershipRow(householdId, userId)?.let { listOf(it).toHouseholdMemberships().first() }) {
            "Just-inserted membership for $userId in $householdId vanished"
        }
    }

    override suspend fun removeMember(householdId: UUID, userId: UUID, at: Instant): Unit = suspendTransaction {
        HouseholdMemberTable.update({
            (HouseholdMemberTable.household_id eq EntityID(householdId, HouseholdTable)) and
                (HouseholdMemberTable.user_id eq EntityID(userId, UserTable)) and
                (HouseholdMemberTable.status eq HouseholdMemberStatus.ACTIVE.name)
        }) {
            it[status] = HouseholdMemberStatus.REMOVED.name
            it[removed_at] = at
            it[server_removed_at] = at
        }
    }

    override suspend fun transferOwnership(householdId: UUID, newOwnerId: UUID, at: Instant): Unit =
        suspendTransaction {
            HouseholdTable.update({ HouseholdTable.id eq EntityID(householdId, HouseholdTable) }) {
                it[owner_id] = EntityID(newOwnerId, UserTable)
                it[updated_at] = at
            }
            HouseholdMemberTable.update({
                (HouseholdMemberTable.household_id eq EntityID(householdId, HouseholdTable)) and
                    (HouseholdMemberTable.user_id eq EntityID(newOwnerId, UserTable)) and
                    (HouseholdMemberTable.status eq HouseholdMemberStatus.ACTIVE.name)
            }) {
                it[role] = HouseholdRole.OWNER.name
            }
        }

    override suspend fun dissolveHousehold(householdId: UUID, at: Instant): Unit = suspendTransaction {
        HouseholdTable.update({ HouseholdTable.id eq EntityID(householdId, HouseholdTable) }) {
            it[deleted_at] = at
            it[updated_at] = at
        }
        HouseholdMemberTable.update({
            (HouseholdMemberTable.household_id eq EntityID(householdId, HouseholdTable)) and
                (HouseholdMemberTable.status eq HouseholdMemberStatus.ACTIVE.name)
        }) {
            it[status] = HouseholdMemberStatus.REMOVED.name
            it[removed_at] = at
            it[server_removed_at] = at
        }
    }

    override suspend fun createInvite(invite: NewHouseholdInvite): HouseholdInvite = suspendTransaction {
        val inviteId = UUID.randomUUID()
        HouseholdInviteTable.insert {
            it[id] = EntityID(inviteId, HouseholdInviteTable)
            it[household_id] = EntityID(invite.householdId, HouseholdTable)
            it[created_by] = EntityID(invite.createdBy, UserTable)
            it[token_hash] = invite.tokenHash
            it[invitee_user_id] = invite.inviteeUserId?.let { userId -> EntityID(userId, UserTable) }
            it[invitee_email] = invite.inviteeEmail
            it[single_use] = invite.singleUse
            it[max_uses] = invite.maxUses
            it[expires_at] = invite.expiresAt
        }
        requireNotNull(loadInvite(inviteId)) { "Just-created invite $inviteId vanished" }
    }

    override suspend fun findInviteByTokenHash(tokenHash: String): HouseholdInvite? = suspendTransaction {
        HouseholdInviteTable.selectAll()
            .where { HouseholdInviteTable.token_hash eq tokenHash }
            .firstOrNull()
            ?.toHouseholdInvite()
    }

    override suspend fun getInvite(inviteId: UUID): HouseholdInvite? = suspendTransaction { loadInvite(inviteId) }

    override suspend fun listOutstandingInvites(householdId: UUID): List<HouseholdInvite> = suspendTransaction {
        HouseholdInviteTable.selectAll()
            .where {
                (HouseholdInviteTable.household_id eq EntityID(householdId, HouseholdTable)) and
                    HouseholdInviteTable.revoked_at.isNull() and
                    HouseholdInviteTable.accepted_at.isNull()
            }
            .orderBy(HouseholdInviteTable.created_at, SortOrder.DESC)
            .map { it.toHouseholdInvite() }
    }

    override suspend fun listPendingInvitesForUser(userId: UUID): List<HouseholdInvite> = suspendTransaction {
        HouseholdInviteTable.selectAll()
            .where {
                (HouseholdInviteTable.invitee_user_id eq EntityID(userId, UserTable)) and
                    HouseholdInviteTable.revoked_at.isNull() and
                    HouseholdInviteTable.accepted_at.isNull()
            }
            .orderBy(HouseholdInviteTable.created_at, SortOrder.DESC)
            .map { it.toHouseholdInvite() }
    }

    override suspend fun revokeInvite(inviteId: UUID, at: Instant): Unit = suspendTransaction {
        HouseholdInviteTable.update({ HouseholdInviteTable.id eq EntityID(inviteId, HouseholdInviteTable) }) {
            it[revoked_at] = at
        }
    }

    override suspend fun recordInviteAcceptance(inviteId: UUID, acceptedBy: UUID, at: Instant): Unit =
        suspendTransaction {
            val invite = HouseholdInviteTable.selectAll()
                .where { HouseholdInviteTable.id eq EntityID(inviteId, HouseholdInviteTable) }
                .firstOrNull() ?: return@suspendTransaction

            HouseholdInviteTable.update({ HouseholdInviteTable.id eq EntityID(inviteId, HouseholdInviteTable) }) {
                it[use_count] = invite[HouseholdInviteTable.use_count] + 1
                if (invite[HouseholdInviteTable.single_use]) {
                    it[accepted_by] = EntityID(acceptedBy, UserTable)
                    it[accepted_at] = at
                }
            }
        }

    /**
     * No-op today: `meal_plans.household_id` and the `grocery_list_item_checks` table don't exist
     * until the plan-sharing and grocery-list PRs land. [HouseholdService.joinByToken] /
     * `acceptInviteById` already call this at the correct point in the join transaction so those
     * PRs only need to fill in this body — no call-site change required.
     */
    override suspend fun bumpServerUpdatedAtForHouseholdRows(householdId: UUID, at: Instant) {
        // Intentionally empty — see KDoc.
    }

    /**
     * No-op today for the same reason as [bumpServerUpdatedAtForHouseholdRows]: nulling
     * `meal_plans.household_id` requires a column that doesn't exist until the plan-sharing PR.
     */
    override suspend fun detachPlansOwnedBy(householdId: UUID, userId: UUID) {
        // Intentionally empty — see KDoc.
    }

    /**
     * Inserts an ACTIVE membership row, translating a losing race against the partial unique index
     * (`idx_household_members_one_active_per_user`) into [AlreadyInHouseholdException] rather than
     * letting the raw SQL exception escape. Any other [ExposedSQLException] is rethrown unchanged —
     * this only recognizes the one specific, expected constraint violation.
     *
     * [at] is null only from [createHousehold], where `joined_at` is left to its column default
     * (a fresh household has no prior transaction step it needs to share a timestamp with).
     */
    private fun insertMembershipOrThrow(householdId: UUID, userId: UUID, role: HouseholdRole, at: Instant?) {
        try {
            HouseholdMemberTable.insert {
                it[id] = EntityID(UUID.randomUUID(), HouseholdMemberTable)
                it[household_id] = EntityID(householdId, HouseholdTable)
                it[user_id] = EntityID(userId, UserTable)
                it[HouseholdMemberTable.role] = role.name
                it[status] = HouseholdMemberStatus.ACTIVE.name
                if (at != null) it[joined_at] = at
            }
        } catch (ex: ExposedSQLException) {
            if (ex.message?.contains("idx_household_members_one_active_per_user", ignoreCase = true) == true) {
                throw AlreadyInHouseholdException("User $userId already belongs to a household")
            }
            throw ex
        }
    }

    private fun activeMembershipRow(householdId: UUID, userId: UUID): ResultRow? =
        HouseholdMemberTable
            .selectAll()
            .where {
                (HouseholdMemberTable.household_id eq EntityID(householdId, HouseholdTable)) and
                    (HouseholdMemberTable.user_id eq EntityID(userId, UserTable)) and
                    (HouseholdMemberTable.status eq HouseholdMemberStatus.ACTIVE.name)
            }
            .firstOrNull()

    private fun loadHousehold(householdId: UUID): Household? {
        val row = HouseholdTable.selectAll()
            .where {
                (HouseholdTable.id eq EntityID(householdId, HouseholdTable)) and HouseholdTable.deleted_at.isNull()
            }
            .firstOrNull() ?: return null

        val members = HouseholdMemberTable.selectAll()
            .where {
                (HouseholdMemberTable.household_id eq EntityID(householdId, HouseholdTable)) and
                    (HouseholdMemberTable.status eq HouseholdMemberStatus.ACTIVE.name)
            }
            .orderBy(HouseholdMemberTable.joined_at, SortOrder.ASC)
            .toList()
            .toHouseholdMemberships()

        return Household(
            id = row[HouseholdTable.id].value,
            name = row[HouseholdTable.name],
            ownerId = row[HouseholdTable.owner_id].value,
            members = members,
        )
    }

    private fun loadInvite(inviteId: UUID): HouseholdInvite? =
        HouseholdInviteTable.selectAll()
            .where { HouseholdInviteTable.id eq EntityID(inviteId, HouseholdInviteTable) }
            .firstOrNull()
            ?.toHouseholdInvite()

    /** Batches a single lookup of [UserTable] for every distinct member rather than one query each. */
    private fun List<ResultRow>.toHouseholdMemberships(): List<HouseholdMembership> {
        if (isEmpty()) return emptyList()
        val userIds = map { it[HouseholdMemberTable.user_id].value }.toSet()
        val usersById = UserTable.selectAll()
            .where { UserTable.id inList userIds.map { userId -> EntityID(userId, UserTable) } }
            .associateBy { it[UserTable.id].value }

        return map { row ->
            val userId = row[HouseholdMemberTable.user_id].value
            val userRow = usersById[userId]
            HouseholdMembership(
                householdId = row[HouseholdMemberTable.household_id].value,
                userId = userId,
                displayName = userRow?.get(UserTable.display_name) ?: "",
                avatarUrl = userRow?.get(UserTable.avatar_url) ?: "",
                role = HouseholdRole.valueOf(row[HouseholdMemberTable.role]),
                status = HouseholdMemberStatus.valueOf(row[HouseholdMemberTable.status]),
                joinedAt = row[HouseholdMemberTable.joined_at],
                removedAt = row[HouseholdMemberTable.removed_at],
                serverRemovedAt = row[HouseholdMemberTable.server_removed_at],
            )
        }
    }

    private fun ResultRow.toHouseholdInvite(): HouseholdInvite = HouseholdInvite(
        id = this[HouseholdInviteTable.id].value,
        householdId = this[HouseholdInviteTable.household_id].value,
        createdBy = this[HouseholdInviteTable.created_by].value,
        tokenHash = this[HouseholdInviteTable.token_hash],
        inviteeUserId = this[HouseholdInviteTable.invitee_user_id]?.value,
        inviteeEmail = this[HouseholdInviteTable.invitee_email],
        singleUse = this[HouseholdInviteTable.single_use],
        maxUses = this[HouseholdInviteTable.max_uses],
        useCount = this[HouseholdInviteTable.use_count],
        expiresAt = this[HouseholdInviteTable.expires_at],
        acceptedBy = this[HouseholdInviteTable.accepted_by]?.value,
        acceptedAt = this[HouseholdInviteTable.accepted_at],
        revokedAt = this[HouseholdInviteTable.revoked_at],
        createdAt = this[HouseholdInviteTable.created_at],
    )
}
