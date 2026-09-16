package com.tenmilelabs.infrastructure.database.integration

import com.tenmilelabs.domain.exception.AlreadyInHouseholdException
import com.tenmilelabs.domain.model.HouseholdMemberStatus
import com.tenmilelabs.domain.model.HouseholdRole
import com.tenmilelabs.domain.util.millisecondPrecisionNow
import com.tenmilelabs.infrastructure.database.initDatabaseAndSchema
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresHouseholdRepository
import com.tenmilelabs.infrastructure.database.tables.HouseholdMemberTable
import com.tenmilelabs.infrastructure.database.tables.HouseholdTable
import com.tenmilelabs.infrastructure.database.tables.UserTable
import kotlinx.coroutines.runBlocking
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
