package com.tenmilelabs.domain.service

import com.tenmilelabs.domain.exception.AlreadyInHouseholdException
import com.tenmilelabs.domain.exception.HouseholdNotFoundException
import com.tenmilelabs.domain.exception.HouseholdValidationException
import com.tenmilelabs.domain.exception.InviteNotFoundException
import com.tenmilelabs.domain.exception.InviteNotForCallerException
import com.tenmilelabs.domain.exception.InviteeNotFoundException
import com.tenmilelabs.domain.exception.NotHouseholdMemberException
import com.tenmilelabs.domain.exception.NotHouseholdOwnerException
import com.tenmilelabs.domain.model.HouseholdRole
import com.tenmilelabs.domain.model.NewHouseholdInvite
import com.tenmilelabs.domain.util.TokenHasher
import com.tenmilelabs.infrastructure.database.FakeHouseholdRepository
import com.tenmilelabs.infrastructure.database.FakeUserRepository
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class HouseholdServiceTest {

    private lateinit var householdRepository: FakeHouseholdRepository
    private lateinit var userRepository: FakeUserRepository
    private lateinit var householdService: HouseholdService
    private val logger = KtorSimpleLogger("HouseholdServiceTest")

    @BeforeEach
    fun setup() {
        householdRepository = FakeHouseholdRepository()
        userRepository = FakeUserRepository()
        householdService = HouseholdService(householdRepository, userRepository, logger)
    }

    // ── Create / one-household enforcement ──────────────────────────────────

    @Test
    fun `createHousehold succeeds and makes the caller the owner`() = runTest {
        val ownerId = UUID.randomUUID()

        val household = householdService.createHousehold("The Muciente House", ownerId)

        assertEquals("The Muciente House", household.name)
        assertEquals(ownerId, household.ownerId)
        assertEquals(1, household.members.size)
        assertEquals(HouseholdRole.OWNER, household.members.single().role)
    }

    @Test
    fun `createHousehold with blank name fails`() = runTest {
        assertFailsWith<HouseholdValidationException> {
            householdService.createHousehold("   ", UUID.randomUUID())
        }
    }

    @Test
    fun `createHousehold fails when caller already belongs to a household`() = runTest {
        val ownerId = UUID.randomUUID()
        householdService.createHousehold("First household", ownerId)

        assertFailsWith<AlreadyInHouseholdException> {
            householdService.createHousehold("Second household", ownerId)
        }
    }

    // ── Owner-only invite / remove ───────────────────────────────────────────

    @Test
    fun `createInvite by a non-owner member fails`() = runTest {
        val ownerId = UUID.randomUUID()
        val household = householdService.createHousehold("Household", ownerId)
        val memberId = joinFreshMember(household.id)

        assertFailsWith<NotHouseholdOwnerException> {
            householdService.createInvite(household.id, memberId, inviteeEmail = null, maxUses = null, expiresInHours = null)
        }
    }

    @Test
    fun `createInvite by a non-member fails`() = runTest {
        val ownerId = UUID.randomUUID()
        val household = householdService.createHousehold("Household", ownerId)

        assertFailsWith<NotHouseholdMemberException> {
            householdService.createInvite(household.id, UUID.randomUUID(), inviteeEmail = null, maxUses = null, expiresInHours = null)
        }
    }

    @Test
    fun `removeMember by a non-owner member fails`() = runTest {
        val ownerId = UUID.randomUUID()
        val household = householdService.createHousehold("Household", ownerId)
        val memberId = joinFreshMember(household.id)
        val otherMemberId = joinFreshMember(household.id)

        assertFailsWith<NotHouseholdOwnerException> {
            householdService.removeMember(household.id, memberId, otherMemberId)
        }
    }

    @Test
    fun `removeMember on self fails with a validation error, not a generic one`() = runTest {
        val ownerId = UUID.randomUUID()
        val household = householdService.createHousehold("Household", ownerId)

        assertFailsWith<HouseholdValidationException> {
            householdService.removeMember(household.id, ownerId, ownerId)
        }
    }

    @Test
    fun `owner removes a member successfully`() = runTest {
        val ownerId = UUID.randomUUID()
        val household = householdService.createHousehold("Household", ownerId)
        val memberId = joinFreshMember(household.id)

        householdService.removeMember(household.id, ownerId, memberId)

        assertNull(householdRepository.getActiveMembership(household.id, memberId))
        assertEquals(1, householdService.listMembers(household.id, ownerId).size)
    }

    // ── Leave: auto-transfer / dissolve ──────────────────────────────────────

    @Test
    fun `leaveHousehold by a non-member fails`() = runTest {
        assertFailsWith<NotHouseholdMemberException> {
            householdService.leaveHousehold(UUID.randomUUID(), UUID.randomUUID())
        }
    }

    @Test
    fun `owner leaving transfers ownership to the earliest-joined remaining member`() = runTest {
        val ownerId = UUID.randomUUID()
        val household = householdService.createHousehold("Household", ownerId)
        val firstJoiner = joinFreshMember(household.id)
        val secondJoiner = joinFreshMember(household.id)

        householdService.leaveHousehold(household.id, ownerId)

        val newOwnerMembership = householdRepository.getActiveMembership(household.id, firstJoiner)
        assertNotNull(newOwnerMembership)
        assertEquals(HouseholdRole.OWNER, newOwnerMembership.role)
        assertEquals(HouseholdRole.MEMBER, householdRepository.getActiveMembership(household.id, secondJoiner)?.role)
        assertNotNull(householdRepository.getHousehold(household.id))
    }

    @Test
    fun `last member leaving dissolves the household`() = runTest {
        val ownerId = UUID.randomUUID()
        val household = householdService.createHousehold("Household", ownerId)

        householdService.leaveHousehold(household.id, ownerId)

        assertNull(householdRepository.getHousehold(household.id))
        assertFailsWith<HouseholdNotFoundException> { householdService.getHouseholdForCaller(ownerId) }
    }

    @Test
    fun `a non-owner member leaving does not transfer ownership or dissolve`() = runTest {
        val ownerId = UUID.randomUUID()
        val household = householdService.createHousehold("Household", ownerId)
        val memberId = joinFreshMember(household.id)

        householdService.leaveHousehold(household.id, memberId)

        assertEquals(HouseholdRole.OWNER, householdRepository.getActiveMembership(household.id, ownerId)?.role)
        assertNotNull(householdRepository.getHousehold(household.id))
    }

    // ── Invite accept: happy path + rejection reasons ────────────────────────

    @Test
    fun `joinByToken happy path adds the caller as a member`() = runTest {
        val ownerId = UUID.randomUUID()
        val household = householdService.createHousehold("Household", ownerId)
        val (_, rawToken) = householdService.createInvite(
            household.id, ownerId, inviteeEmail = null, maxUses = null, expiresInHours = null
        )
        val joinerId = UUID.randomUUID()

        val joined = householdService.joinByToken(rawToken, joinerId)

        assertEquals(household.id, joined.id)
        assertEquals(HouseholdRole.MEMBER, householdRepository.getActiveMembership(household.id, joinerId)?.role)
    }

    @Test
    fun `acceptInviteById happy path adds the caller as a member`() = runTest {
        val ownerId = UUID.randomUUID()
        val household = householdService.createHousehold("Household", ownerId)
        val (invite, _) = householdService.createInvite(
            household.id, ownerId, inviteeEmail = null, maxUses = null, expiresInHours = null
        )
        val joinerId = UUID.randomUUID()

        householdService.acceptInviteById(invite.id, joinerId)

        assertEquals(HouseholdRole.MEMBER, householdRepository.getActiveMembership(household.id, joinerId)?.role)
    }

    @Test
    fun `joinByToken with an unknown token fails uniformly as not found`() = runTest {
        assertFailsWith<InviteNotFoundException> {
            householdService.joinByToken("not-a-real-token", UUID.randomUUID())
        }
    }

    @Test
    fun `joinByToken with an expired invite fails as not found`() = runTest {
        val ownerId = UUID.randomUUID()
        val household = householdService.createHousehold("Household", ownerId)
        val rawToken = seedRawInvite(
            householdId = household.id,
            createdBy = ownerId,
            expiresAt = Clock.System.now().minus(1.hours),
        )

        assertFailsWith<InviteNotFoundException> {
            householdService.joinByToken(rawToken, UUID.randomUUID())
        }
    }

    @Test
    fun `joinByToken with a revoked invite fails as not found`() = runTest {
        val ownerId = UUID.randomUUID()
        val household = householdService.createHousehold("Household", ownerId)
        val (invite, rawToken) = householdService.createInvite(
            household.id, ownerId, inviteeEmail = null, maxUses = null, expiresInHours = null
        )
        householdService.revokeInvite(household.id, ownerId, invite.id)

        assertFailsWith<InviteNotFoundException> {
            householdService.joinByToken(rawToken, UUID.randomUUID())
        }
    }

    @Test
    fun `joinByToken with an already-accepted single-use invite fails as exhausted`() = runTest {
        val ownerId = UUID.randomUUID()
        val household = householdService.createHousehold("Household", ownerId)
        val (_, rawToken) = householdService.createInvite(
            household.id, ownerId, inviteeEmail = null, singleUse = true, maxUses = null, expiresInHours = null
        )
        householdService.joinByToken(rawToken, UUID.randomUUID())

        assertFailsWith<InviteNotFoundException> {
            householdService.joinByToken(rawToken, UUID.randomUUID())
        }
    }

    @Test
    fun `joinByToken addressed to a different user fails as not for caller`() = runTest {
        val ownerId = UUID.randomUUID()
        val household = householdService.createHousehold("Household", ownerId)
        val invitee = userRepository.createUser("invitee@example.com", "invitee", "hash")
        assertNotNull(invitee)
        val (_, rawToken) = householdService.createInvite(
            household.id, ownerId, inviteeEmail = "invitee@example.com", maxUses = null, expiresInHours = null
        )

        assertFailsWith<InviteNotForCallerException> {
            householdService.joinByToken(rawToken, UUID.randomUUID())
        }
    }

    @Test
    fun `createInvite for an email with no matching account fails`() = runTest {
        val ownerId = UUID.randomUUID()
        val household = householdService.createHousehold("Household", ownerId)

        assertFailsWith<InviteeNotFoundException> {
            householdService.createInvite(
                household.id, ownerId, inviteeEmail = "nobody@example.com", maxUses = null, expiresInHours = null
            )
        }
    }

    @Test
    fun `joinByToken fails when the caller already belongs to another household`() = runTest {
        val ownerA = UUID.randomUUID()
        val householdA = householdService.createHousehold("Household A", ownerA)
        val ownerB = UUID.randomUUID()
        val householdB = householdService.createHousehold("Household B", ownerB)
        val (_, rawToken) = householdService.createInvite(
            householdB.id, ownerB, inviteeEmail = null, maxUses = null, expiresInHours = null
        )
        val alreadyMember = joinFreshMember(householdA.id)

        assertFailsWith<AlreadyInHouseholdException> {
            householdService.joinByToken(rawToken, alreadyMember)
        }
    }

    // ── Decline / revoke ──────────────────────────────────────────────────────

    @Test
    fun `declineInvite by the invitee revokes it without joining`() = runTest {
        val ownerId = UUID.randomUUID()
        val household = householdService.createHousehold("Household", ownerId)
        val invitee = userRepository.createUser("declines@example.com", "decliner", "hash")
        assertNotNull(invitee)
        val (invite, _) = householdService.createInvite(
            household.id, ownerId, inviteeEmail = "declines@example.com", maxUses = null, expiresInHours = null
        )

        householdService.declineInvite(invite.id, invitee.uuid)

        assertNull(householdRepository.getActiveMembership(household.id, invitee.uuid))
        assertTrue(householdService.listOutstandingInvites(household.id, ownerId).isEmpty())
    }

    @Test
    fun `declineInvite by someone other than the invitee fails`() = runTest {
        val ownerId = UUID.randomUUID()
        val household = householdService.createHousehold("Household", ownerId)
        val invitee = userRepository.createUser("target@example.com", "target", "hash")
        assertNotNull(invitee)
        val (invite, _) = householdService.createInvite(
            household.id, ownerId, inviteeEmail = "target@example.com", maxUses = null, expiresInHours = null
        )

        assertFailsWith<InviteNotForCallerException> {
            householdService.declineInvite(invite.id, UUID.randomUUID())
        }
    }

    private suspend fun joinFreshMember(householdId: UUID): UUID {
        val ownerHousehold = requireNotNull(householdRepository.getHousehold(householdId))
        val (_, rawToken) = householdService.createInvite(
            householdId, ownerHousehold.ownerId, inviteeEmail = null, maxUses = null, expiresInHours = null
        )
        val joinerId = UUID.randomUUID()
        householdService.joinByToken(rawToken, joinerId)
        return joinerId
    }

    private suspend fun seedRawInvite(
        householdId: UUID,
        createdBy: UUID,
        expiresAt: Instant,
    ): String {
        val rawToken = TokenHasher.generateSecureToken()
        householdRepository.createInvite(
            NewHouseholdInvite(
                householdId = householdId,
                createdBy = createdBy,
                tokenHash = TokenHasher.sha256Base64(rawToken),
                inviteeUserId = null,
                inviteeEmail = null,
                singleUse = true,
                maxUses = null,
                expiresAt = expiresAt,
            )
        )
        return rawToken
    }
}
