package com.tenmilelabs.infrastructure.database.integration

import com.tenmilelabs.domain.exception.AlreadyInHouseholdException
import com.tenmilelabs.domain.exception.InviteNotFoundException
import com.tenmilelabs.domain.model.HouseholdMemberStatus
import com.tenmilelabs.domain.model.HouseholdRole
import com.tenmilelabs.domain.model.NewHouseholdInvite
import com.tenmilelabs.domain.util.millisecondPrecisionNow
import com.tenmilelabs.infrastructure.database.initDatabaseAndSchema
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresHouseholdRepository
import com.tenmilelabs.infrastructure.database.tables.GroceryListItemCheckTable
import com.tenmilelabs.infrastructure.database.tables.HouseholdMemberTable
import com.tenmilelabs.infrastructure.database.tables.HouseholdTable
import com.tenmilelabs.infrastructure.database.tables.MealPlanTable
import com.tenmilelabs.infrastructure.database.tables.UserTable
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

@Tag("db-integration")
class PostgresHouseholdRepositoryIntegrationTest {

    @BeforeEach
    fun resetSchema() {
        Database.connect(
            url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/chefai_test",
            user = System.getenv("DB_USER") ?: "postgres",
            password = System.getenv("DB_PASSWORD") ?: "password"
        )

        transaction {
            exec("DROP SCHEMA IF EXISTS public CASCADE")
            exec("CREATE SCHEMA public")
        }

        initDatabaseAndSchema()
    }

    @Test
    fun createHouseholdThenGetHouseholdRoundTrips() = runBlocking {
        val repo = PostgresHouseholdRepository()
        val ownerId = seedUser()

        val created = repo.createHousehold("The Muciente House", ownerId)
        val fetched = repo.getHousehold(created.id)

        assertNotNull(fetched)
        assertEquals("The Muciente House", fetched.name)
        assertEquals(ownerId, fetched.ownerId)
        assertEquals(1, fetched.members.size)
        assertEquals(HouseholdRole.OWNER, fetched.members.single().role)
    }

    @Test
    fun secondActiveMembershipForTheSameUserFailsOnThePartialUniqueIndex() = runBlocking {
        val repo = PostgresHouseholdRepository()
        val ownerA = seedUser()
        val ownerB = seedUser()
        val floater = seedUser()

        val householdA = repo.createHousehold("Household A", ownerA)
        val householdB = repo.createHousehold("Household B", ownerB)

        repo.addMember(householdA.id, floater, HouseholdRole.MEMBER, millisecondPrecisionNow())

        assertFailsWith<AlreadyInHouseholdException> {
            repo.addMember(householdB.id, floater, HouseholdRole.MEMBER, millisecondPrecisionNow())
        }

        // The losing insert must not have silently partially succeeded.
        assertNotNull(repo.getActiveMembership(householdA.id, floater))
        assertNull(repo.getActiveMembership(householdB.id, floater))
    }

    @Test
    fun ownerInvariantHoldsAfterCreateRenameTransferAndDissolve() = runBlocking {
        val repo = PostgresHouseholdRepository()
        val originalOwner = seedUser()
        val household = repo.createHousehold("Household", originalOwner)
        assertOwnerInvariant(household.id)

        repo.renameHousehold(household.id, "Renamed Household", millisecondPrecisionNow())
        assertOwnerInvariant(household.id)

        val newOwner = seedUser()
        repo.addMember(household.id, newOwner, HouseholdRole.MEMBER, millisecondPrecisionNow())
        repo.removeMember(household.id, originalOwner, millisecondPrecisionNow())
        repo.transferOwnership(household.id, newOwner, millisecondPrecisionNow())
        assertOwnerInvariant(household.id)

        repo.dissolveHousehold(household.id, millisecondPrecisionNow())
        assertNull(repo.getHousehold(household.id))
        assertTrue(repo.listActiveMembers(household.id).isEmpty())
    }

    @Test
    fun removeMemberDoesNotDisturbTheOwnerInvariant() = runBlocking {
        val repo = PostgresHouseholdRepository()
        val ownerId = seedUser()
        val household = repo.createHousehold("Household", ownerId)
        val memberId = seedUser()
        repo.addMember(household.id, memberId, HouseholdRole.MEMBER, millisecondPrecisionNow())

        repo.removeMember(household.id, memberId, millisecondPrecisionNow())

        assertOwnerInvariant(household.id)
        assertNull(repo.getActiveMembership(household.id, memberId))
    }

    @Test
    fun detachPlansOwnedByNullsOnlyTheDepartingMembersOwnPlans() = runBlocking {
        val repo = PostgresHouseholdRepository()
        val ownerId = seedUser()
        val memberId = seedUser()
        val household = repo.createHousehold("Household", ownerId)
        repo.addMember(household.id, memberId, HouseholdRole.MEMBER, millisecondPrecisionNow())

        val ownersPlanId = seedMealPlan(ownerId, household.id)
        val membersPlanId = seedMealPlan(memberId, household.id)

        val detachedAt = millisecondPrecisionNow()
        repo.detachPlansOwnedBy(household.id, memberId, detachedAt)

        assertNull(mealPlanHouseholdId(membersPlanId), "the departing member's own plan must revert to personal")
        assertEquals(household.id, mealPlanHouseholdId(ownersPlanId), "other members' plans must stay shared")
        assertEquals(
            household.id,
            mealPlanFormerHouseholdId(membersPlanId),
            "the detached plan must record which household it was shared with"
        )
        assertEquals(detachedAt, mealPlanHouseholdDetachedAt(membersPlanId))
        assertNull(mealPlanFormerHouseholdId(ownersPlanId), "an untouched plan must not gain a former_household_id")
    }

    @Test
    fun bumpServerUpdatedAtForHouseholdRowsOnlyTouchesRowsUnderThatHousehold() = runBlocking {
        val repo = PostgresHouseholdRepository()
        val ownerId = seedUser()
        val household = repo.createHousehold("Household", ownerId)
        val otherOwnerId = seedUser()
        val otherHousehold = repo.createHousehold("Other Household", otherOwnerId)

        val sharedPlanId = seedMealPlan(ownerId, household.id, serverUpdatedAtMillis = 1_000L)
        val otherHouseholdPlanId = seedMealPlan(otherOwnerId, otherHousehold.id, serverUpdatedAtMillis = 1_000L)
        seedGroceryItem(sharedPlanId, "eggs", serverUpdatedAtMillis = 1_000L)
        seedGroceryItem(otherHouseholdPlanId, "milk", serverUpdatedAtMillis = 1_000L)

        repo.bumpServerUpdatedAtForHouseholdRows(household.id, millisecondPrecisionNow())

        assertTrue(mealPlanServerUpdatedAtMillis(sharedPlanId) > 1_000L, "the bumped household's plan must advance")
        assertEquals(1_000L, mealPlanServerUpdatedAtMillis(otherHouseholdPlanId), "an unrelated household's plan must be untouched")
        assertTrue(groceryItemServerUpdatedAtMillis(sharedPlanId, "eggs") > 1_000L, "the bumped household's grocery item must advance")
        assertEquals(
            1_000L,
            groceryItemServerUpdatedAtMillis(otherHouseholdPlanId, "milk"),
            "an unrelated household's grocery item must be untouched"
        )
    }

    /**
     * Dissolve used to leave every plan pointing at the deleted household, so each former member's
     * next pull tombstoned their own plans. It must detach them like a departure does, and revoke
     * outstanding invites; accepting one afterwards is a dead invite, not a 500.
     */
    @Test
    fun dissolveDetachesEveryPlanRevokesInvitesAndRejectsLaterAccepts() = runBlocking {
        val repo = PostgresHouseholdRepository()
        val ownerId = seedUser()
        val memberId = seedUser()
        val latecomer = seedUser()
        val household = repo.createHousehold("Household", ownerId)
        repo.addMember(household.id, memberId, HouseholdRole.MEMBER, millisecondPrecisionNow())
        val ownersPlanId = seedMealPlan(ownerId, household.id)
        val membersPlanId = seedMealPlan(memberId, household.id)
        val invite = repo.createInvite(
            NewHouseholdInvite(
                householdId = household.id,
                createdBy = ownerId,
                tokenHash = "hash-${UUID.randomUUID()}",
                inviteeUserId = latecomer,
                inviteeEmail = null,
                singleUse = true,
                maxUses = null,
                expiresAt = millisecondPrecisionNow().plus(30.days)
            )
        )

        repo.dissolveHousehold(household.id, millisecondPrecisionNow())

        for (planId in listOf(ownersPlanId, membersPlanId)) {
            assertNull(mealPlanHouseholdId(planId))
            val formerHouseholdId = transaction {
                MealPlanTable.selectAll().where { MealPlanTable.id eq planId }.first()[MealPlanTable.former_household_id]?.value
            }
            assertEquals(household.id, formerHouseholdId)
        }
        assertTrue(repo.listPendingInvitesForUser(latecomer).isEmpty())
        assertFailsWith<InviteNotFoundException> {
            repo.acceptInvite(invite.id, latecomer, millisecondPrecisionNow())
        }
        assertNull(repo.getActiveMembership(household.id, latecomer))
    }

    private fun seedMealPlan(ownerId: UUID, householdId: UUID, serverUpdatedAtMillis: Long = 0L): UUID {
        val planId = UUID.randomUUID()
        transaction {
            MealPlanTable.insert {
                it[id] = EntityID(planId, MealPlanTable)
                it[user_id] = EntityID(ownerId, UserTable)
                it[household_id] = EntityID(householdId, HouseholdTable)
                it[name] = "Week Plan"
                it[status] = "DRAFT"
                it[preferences] = "{}"
                it[created_at] = 0L
                it[updated_at] = 0L
                it[deleted_at] = null
                it[server_updated_at] = Instant.fromEpochMilliseconds(serverUpdatedAtMillis)
            }
        }
        return planId
    }

    private fun mealPlanHouseholdId(planId: UUID): UUID? = transaction {
        MealPlanTable.selectAll().where { MealPlanTable.id eq planId }.first()[MealPlanTable.household_id]?.value
    }

    private fun mealPlanFormerHouseholdId(planId: UUID): UUID? = transaction {
        MealPlanTable.selectAll().where { MealPlanTable.id eq planId }.first()[MealPlanTable.former_household_id]?.value
    }

    private fun mealPlanHouseholdDetachedAt(planId: UUID): Instant? = transaction {
        MealPlanTable.selectAll().where { MealPlanTable.id eq planId }.first()[MealPlanTable.household_detached_at]
    }

    private fun mealPlanServerUpdatedAtMillis(planId: UUID): Long = transaction {
        MealPlanTable.selectAll().where { MealPlanTable.id eq planId }
            .first()[MealPlanTable.server_updated_at].toEpochMilliseconds()
    }

    private fun seedGroceryItem(planId: UUID, itemKey: String, serverUpdatedAtMillis: Long) = transaction {
        GroceryListItemCheckTable.insert {
            it[meal_plan_id] = EntityID(planId, MealPlanTable)
            it[item_key] = itemKey
            it[checked] = false
            it[checked_by] = null
            it[updated_at] = 0L
            it[deleted_at] = null
            it[server_updated_at] = Instant.fromEpochMilliseconds(serverUpdatedAtMillis)
        }
        Unit
    }

    private fun groceryItemServerUpdatedAtMillis(planId: UUID, itemKey: String): Long = transaction {
        GroceryListItemCheckTable.selectAll()
            .where { (GroceryListItemCheckTable.meal_plan_id eq EntityID(planId, MealPlanTable)) and (GroceryListItemCheckTable.item_key eq itemKey) }
            .first()[GroceryListItemCheckTable.server_updated_at].toEpochMilliseconds()
    }

    /** `households.owner_id` must always equal exactly one ACTIVE, OWNER-role `household_members` row. */
    private fun assertOwnerInvariant(householdId: UUID) = transaction {
        val ownerId = HouseholdTable
            .selectAll()
            .where { HouseholdTable.id eq EntityID(householdId, HouseholdTable) }
            .first()[HouseholdTable.owner_id].value

        val ownerMemberships = HouseholdMemberTable
            .selectAll()
            .where {
                (HouseholdMemberTable.household_id eq EntityID(householdId, HouseholdTable)) and
                    (HouseholdMemberTable.status eq HouseholdMemberStatus.ACTIVE.name) and
                    (HouseholdMemberTable.role eq HouseholdRole.OWNER.name)
            }
            .toList()

        assertEquals(1, ownerMemberships.size, "Expected exactly one ACTIVE OWNER membership row")
        assertEquals(ownerId, ownerMemberships.single()[HouseholdMemberTable.user_id].value)
    }

    private fun seedUser(): UUID {
        val userId = UUID.randomUUID()
        transaction {
            UserTable.insert {
                it[UserTable.id] = EntityID(userId, UserTable)
                it[user_name] = "user"
                it[email] = "user-${userId}@example.com"
                it[display_name] = "User"
                it[avatar_url] = ""
                it[password_hash] = "hash"
            }
        }
        return userId
    }
}
