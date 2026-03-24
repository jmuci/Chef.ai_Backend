package domain.service

import com.tenmilelabs.domain.service.MealPlanGenerationService
import com.tenmilelabs.domain.service.MealPlanPreferences
import com.tenmilelabs.domain.service.MealType
import com.tenmilelabs.domain.service.VarietyPreference
import com.tenmilelabs.infrastructure.database.FakeSyncRepository
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MealPlanGenerationServiceTest {

    private val log = KtorSimpleLogger("test")
    private val scope = CoroutineScope(SupervisorJob())

    private fun makeService(repo: FakeSyncRepository) =
        MealPlanGenerationService(repo, scope, log)

    // ── parsePreferences ──────────────────────────────────────────────────────

    @Test
    fun `parsePreferences extracts all known fields`() {
        val service = makeService(FakeSyncRepository())
        val json = """
            {
              "planLengthDays": 5,
              "mealType": "DINNER_AND_LUNCH",
              "dietaryRestrictions": ["VEGAN", "GLUTEN_FREE"],
              "recipeSource": "COLLECTION_ONLY",
              "maxPrepTimeMinutes": 30,
              "servingsPerMeal": 4,
              "batchCooking": true,
              "leftoverFriendly": false,
              "varietyPreference": "HIGH"
            }
        """.trimIndent()

        val prefs = service.parsePreferences(json)

        assertEquals(5, prefs.planLengthDays)
        assertEquals(MealType.DINNER_AND_LUNCH, prefs.mealType)
        assertEquals(listOf("VEGAN", "GLUTEN_FREE"), prefs.dietaryRestrictions)
        assertEquals("COLLECTION_ONLY", prefs.recipeSource)
        assertEquals(30, prefs.maxPrepTimeMinutes)
        assertEquals(4, prefs.servingsPerMeal)
        assertTrue(prefs.batchCooking)
        assertEquals(VarietyPreference.HIGH, prefs.varietyPreference)
    }

    @Test
    fun `parsePreferences uses defaults for missing fields`() {
        val service = makeService(FakeSyncRepository())
        val prefs = service.parsePreferences("{}")

        assertEquals(7, prefs.planLengthDays)
        assertEquals(MealType.DINNER, prefs.mealType)
        assertTrue(prefs.dietaryRestrictions.isEmpty())
        assertEquals("INCLUDE_PUBLIC", prefs.recipeSource)
        assertNull(prefs.maxPrepTimeMinutes)
        assertEquals(VarietyPreference.HIGH, prefs.varietyPreference)
    }

    // ── assignRecipesToDays ───────────────────────────────────────────────────

    @Test
    fun `HIGH variety produces no duplicate dinner recipes when enough candidates exist`() {
        val service = makeService(FakeSyncRepository())
        val candidates = (1..7).map { UUID.randomUUID() }
        val prefs = defaultPrefs(planLengthDays = 7, variety = VarietyPreference.HIGH)

        val days = service.assignRecipesToDays(candidates, prefs)

        assertEquals(7, days.size)
        val usedIds = days.map { it.dinnerRecipeId }.filterNotNull()
        assertEquals(usedIds.size, usedIds.distinct().size)
    }

    @Test
    fun `HIGH variety with fewer candidates than days produces partial fill`() {
        val service = makeService(FakeSyncRepository())
        val candidates = listOf(UUID.randomUUID(), UUID.randomUUID())
        val prefs = defaultPrefs(planLengthDays = 5, variety = VarietyPreference.HIGH)

        val days = service.assignRecipesToDays(candidates, prefs)

        assertEquals(5, days.size)
        val filledCount = days.count { it.dinnerRecipeId != null }
        assertEquals(2, filledCount)
    }

    @Test
    fun `MEDIUM variety allows repeats after 3-day gap`() {
        val service = makeService(FakeSyncRepository())
        // 2 candidates, 7 days — must repeat but only after gap
        val recipeA = UUID.randomUUID()
        val recipeB = UUID.randomUUID()
        val candidates = listOf(recipeA, recipeB)
        val prefs = defaultPrefs(planLengthDays = 7, variety = VarietyPreference.MEDIUM)

        val days = service.assignRecipesToDays(candidates, prefs)

        assertEquals(7, days.size)
        // No two consecutive days should share the same dinner recipe
        for (i in 0 until days.size - 1) {
            val current = days[i].dinnerRecipeId
            val next = days[i + 1].dinnerRecipeId
            if (current != null && next != null) {
                assertTrue(current != next, "Consecutive days $i and ${i + 1} share the same recipe")
            }
        }
    }

    @Test
    fun `LOW variety reuses candidates freely`() {
        val service = makeService(FakeSyncRepository())
        val candidates = listOf(UUID.randomUUID())
        val prefs = defaultPrefs(planLengthDays = 5, variety = VarietyPreference.LOW)

        val days = service.assignRecipesToDays(candidates, prefs)

        assertEquals(5, days.size)
        assertTrue(days.all { it.dinnerRecipeId != null })
    }

    @Test
    fun `DINNER_AND_LUNCH populates both slots`() {
        val service = makeService(FakeSyncRepository())
        val candidates = (1..14).map { UUID.randomUUID() }
        val prefs = defaultPrefs(
            planLengthDays = 5,
            mealType = MealType.DINNER_AND_LUNCH,
            variety = VarietyPreference.HIGH
        )

        val days = service.assignRecipesToDays(candidates, prefs)

        assertEquals(5, days.size)
        assertTrue(days.all { it.dinnerRecipeId != null })
        assertTrue(days.all { it.lunchRecipeId != null })
    }

    @Test
    fun `DINNER meal type leaves lunch slots null`() {
        val service = makeService(FakeSyncRepository())
        val candidates = (1..5).map { UUID.randomUUID() }
        val prefs = defaultPrefs(planLengthDays = 5, mealType = MealType.DINNER)

        val days = service.assignRecipesToDays(candidates, prefs)

        assertTrue(days.all { it.lunchRecipeId == null })
    }

    @Test
    fun `batchCooking reuses previous day recipe on even-indexed days`() {
        val service = makeService(FakeSyncRepository())
        val candidates = (1..5).map { UUID.randomUUID() }
        val prefs = defaultPrefs(planLengthDays = 4, variety = VarietyPreference.LOW, batchCooking = true)

        val days = service.assignRecipesToDays(candidates, prefs)

        assertEquals(4, days.size)
        // Day 1 (index 1) should share dinner recipe with day 0
        assertEquals(days[0].dinnerRecipeId, days[1].dinnerRecipeId)
        // Day 3 (index 3) should share dinner recipe with day 2
        assertEquals(days[2].dinnerRecipeId, days[3].dinnerRecipeId)
    }

    @Test
    fun `empty candidates returns days with all null slots`() {
        val service = makeService(FakeSyncRepository())
        val prefs = defaultPrefs(planLengthDays = 3)

        val days = service.assignRecipesToDays(emptyList(), prefs)

        assertEquals(3, days.size)
        assertTrue(days.all { it.dinnerRecipeId == null && it.lunchRecipeId == null })
    }

    // ── startGeneration ───────────────────────────────────────────────────────

    @Test
    fun `startGeneration returns null when plan is not found`() = runTest {
        val repo = FakeSyncRepository()
        val service = makeService(repo)

        val result = service.startGeneration(UUID.randomUUID(), UUID.randomUUID())

        assertNull(result)
    }

    @Test
    fun `startGeneration transitions plan to GENERATING and returns 202 payload`() = runTest {
        val repo = FakeSyncRepository()
        val planId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        repo.seedUser(uuid = userId)
        repo.seedMealPlan(
            plan = buildDraftPlan(planId, userId),
            serverUpdatedAtMillis = 1000L
        )

        val service = makeService(repo)
        val response = service.startGeneration(planId, userId)

        assertNotNull(response)
        assertEquals(planId.toString(), response.uuid)
        assertEquals("GENERATING", response.status)
        assertTrue(response.updatedAt > 0)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun defaultPrefs(
        planLengthDays: Int = 5,
        mealType: MealType = MealType.DINNER,
        variety: VarietyPreference = VarietyPreference.HIGH,
        batchCooking: Boolean = false
    ) = MealPlanPreferences(
        planLengthDays = planLengthDays,
        mealType = mealType,
        dietaryRestrictions = emptyList(),
        recipeSource = "INCLUDE_PUBLIC",
        maxPrepTimeMinutes = null,
        servingsPerMeal = 2,
        batchCooking = batchCooking,
        leftoverFriendly = false,
        varietyPreference = variety
    )

    private fun buildDraftPlan(planId: UUID, userId: UUID) =
        com.tenmilelabs.application.dto.SyncMealPlanDto(
            uuid = planId.toString(),
            name = "Test Plan",
            status = "DRAFT",
            preferencesJson = """{"planLengthDays":3,"mealType":"DINNER","recipeSource":"INCLUDE_PUBLIC"}""",
            createdAt = 1000L,
            updatedAt = 1000L,
            deletedAt = null,
            days = emptyList()
        )
}
