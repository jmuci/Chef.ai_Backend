package com.tenmilelabs.domain.repository

import com.tenmilelabs.domain.model.DepartureOutcome
import com.tenmilelabs.domain.model.Household
import com.tenmilelabs.domain.model.HouseholdInvite
import com.tenmilelabs.domain.model.HouseholdMembership
import com.tenmilelabs.domain.model.HouseholdRole
import com.tenmilelabs.domain.model.NewHouseholdInvite
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Pure persistence for households, membership, and invites — no authorization or business rules.
 * Those live in [com.tenmilelabs.domain.service.HouseholdService]; this interface deliberately
 * trusts every caller, matching the split [SyncRepository]/`SyncService` already establish.
 */
interface HouseholdRepository {
    suspend fun createHousehold(name: String, ownerId: UUID): Household
    suspend fun getHousehold(id: UUID): Household?

    /** At most one row can ever match — see the partial unique index on `household_members`. */
    suspend fun getActiveHouseholdForUser(userId: UUID): Household?
    suspend fun getActiveMembership(householdId: UUID, userId: UUID): HouseholdMembership?
    suspend fun listActiveMembers(householdId: UUID): List<HouseholdMembership>

    suspend fun renameHousehold(householdId: UUID, name: String, at: Instant)

    /**
     * Inserts an ACTIVE membership row. On a losing race against the partial unique index (the
     * user already has an active membership elsewhere), throws
     * [com.tenmilelabs.domain.exception.AlreadyInHouseholdException] directly rather than a
     * generic wrapper — the implementation recognizes that specific constraint violation and
     * translates it itself, so [HouseholdService] needs no special-case handling around this call;
     * the exception just propagates like any other domain exception.
     */
    suspend fun addMember(householdId: UUID, userId: UUID, role: HouseholdRole, at: Instant): HouseholdMembership

    /** Marks the membership REMOVED and stamps both `removed_at` and `server_removed_at` with
     *  [at]. Does not touch meal plans — callers detach those separately via
     *  [detachPlansOwnedBy]. */
    suspend fun removeMember(householdId: UUID, userId: UUID, at: Instant)

    /** Updates both [HouseholdTable.owner_id] and the two members' roles in one transaction. */
    suspend fun transferOwnership(householdId: UUID, newOwnerId: UUID, at: Instant)

    /** Soft-deletes the household and marks every remaining ACTIVE member REMOVED (each gets a
     *  fresh `server_removed_at`, which is what drives client-side tombstones for every one of
     *  them, not just the member who triggered the dissolution). */
    suspend fun dissolveHousehold(householdId: UUID, at: Instant)

    suspend fun createInvite(invite: NewHouseholdInvite): HouseholdInvite
    suspend fun findInviteByTokenHash(tokenHash: String): HouseholdInvite?
    suspend fun getInvite(inviteId: UUID): HouseholdInvite?
    suspend fun listOutstandingInvites(householdId: UUID): List<HouseholdInvite>
    suspend fun listPendingInvitesForUser(userId: UUID): List<HouseholdInvite>
    suspend fun revokeInvite(inviteId: UUID, at: Instant)

    /** Increments `use_count` and, when the invite is single-use, stamps `accepted_by`/
     *  `accepted_at`. Callers must have already inserted the membership row first — see
     *  [HouseholdService.acceptInvite] for why that ordering is what makes the invite-budget
     *  race-safe without extra locking. */
    suspend fun recordInviteAcceptance(inviteId: UUID, acceptedBy: UUID, at: Instant)

    /**
     * Cursor backfill on join: bumps `server_updated_at` on every meal plan under [householdId]
     * and every grocery-list-item-check row under those plans, to [at]. This is what makes a
     * newly joined member's next `/sync/pull` actually receive rows that predate their own
     * cursor — see the backend prompt's "Cursor backfill on join" section for the tradeoff
     * against a per-source cursor floor.
     *
     * A no-op today (`grocery_list_item_checks` and the `meal_plans.household_id` widening don't
     * exist until B3/B5), implemented here so [HouseholdService.acceptInvite] has a single,
     * stable call site to build on rather than a TODO that's easy to forget.
     */
    suspend fun bumpServerUpdatedAtForHouseholdRows(householdId: UUID, at: Instant)

    /**
     * Nulls `meal_plans.household_id` for every plan owned by [userId] under [householdId] —
     * the plan reverts to personal, still owned by whoever created it. A no-op today for the same
     * reason as [bumpServerUpdatedAtForHouseholdRows]; wired to a real column in B3.
     */
    suspend fun detachPlansOwnedBy(householdId: UUID, userId: UUID)

    /**
     * Atomically departs [departingUserId] from [householdId]: marks their membership REMOVED,
     * detaches plans they own, and — re-reading who's left in the SAME transaction — either
     * transfers ownership to the earliest-joined remaining member or dissolves the household if
     * none remain. Implementations must serialize this against any other concurrent
     * departure/transfer on the same household (e.g. by locking the household row for the
     * duration): running [removeMember], [detachPlansOwnedBy] and [transferOwnership]/
     * [dissolveHousehold] as separate, independently-committing calls left a window where two
     * members leaving at once could each act on a stale snapshot of "who's left" and "was I the
     * owner," leaving `households.owner_id` pointing at a member who was just removed. See
     * [com.tenmilelabs.domain.service.HouseholdService]'s private `departFromHousehold`.
     *
     * Throws [com.tenmilelabs.domain.exception.NotHouseholdMemberException] if [departingUserId]
     * is not currently an ACTIVE member of [householdId].
     */
    suspend fun departFromHousehold(householdId: UUID, departingUserId: UUID, at: Instant): DepartureOutcome

    /**
     * Atomically accepts the invite [inviteId] on behalf of [callerId]: re-validates
     * [com.tenmilelabs.domain.model.HouseholdInvite.isUsable] against the live, locked invite row,
     * inserts the membership, records the acceptance, and bumps the household's shared rows — all
     * in one transaction. Implementations must lock the invite row for the duration so a second,
     * concurrent acceptor of the same single-use invite blocks until the first commits, then
     * re-evaluates usability against the now-consumed row rather than a stale snapshot — otherwise
     * two different callers can both pass an [isUsable][com.tenmilelabs.domain.model.HouseholdInvite.isUsable]
     * check taken before either commits, and a single-use invite silently admits two members.
     *
     * Throws [com.tenmilelabs.domain.exception.InviteNotFoundException] if [inviteId] doesn't
     * resolve, or no longer passes `isUsable` once re-checked under lock.
     * Throws [com.tenmilelabs.domain.exception.InviteNotForCallerException] if the invite names a
     * different invitee. Throws [com.tenmilelabs.domain.exception.AlreadyInHouseholdException] on
     * the same one-household-per-user race [addMember] recognizes.
     */
    suspend fun acceptInvite(inviteId: UUID, callerId: UUID, at: Instant): Household
}
