package com.tenmilelabs.domain.service

import com.tenmilelabs.application.dto.GenerateMealPlanResponse
import com.tenmilelabs.application.dto.SyncMealPlanDayDto
import com.tenmilelabs.domain.repository.RecipeRankingMetadata
import com.tenmilelabs.domain.repository.SyncRepository
import io.ktor.util.logging.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.collections.ArrayDeque
import kotlin.collections.List
import kotlin.collections.emptyList
import kotlin.collections.filter
import kotlin.collections.firstOrNull
import kotlin.collections.last
import kotlin.collections.map
import kotlin.collections.mutableListOf
import kotlin.collections.plusAssign
import kotlin.collections.shuffled
import kotlin.collections.takeLast
import kotlin.collections.toSet

/**
 * Handles the async AI-style meal plan generation pipeline.
 *
 * Flow:
 * 1. [startGeneration] validates ownership, transitions plan to GENERATING, returns 202 payload.
 * 2. A background coroutine runs [generateAsync]: finds candidate recipes, assigns them to days,
 *    replaces meal_plan_days rows, then sets status to READY.
 */
class MealPlanGenerationService(
    private val syncRepository: SyncRepository,
    private val generationScope: CoroutineScope,
    private val log: Logger
) {
    /**
     * Validates that [userId] owns [mealPlanId], transitions the plan to GENERATING status,
     * launches the async generation pipeline, and returns the 202 payload.
     *
     * Returns null if the plan is not found or not owned by [userId].
     */
    suspend fun startGeneration(mealPlanId: UUID, userId: UUID): GenerateMealPlanResponse? {
        val record = syncRepository.getMealPlanForUser(mealPlanId, userId) ?: return null

        val now = Clock.System.now()
        syncRepository.updateMealPlanStatus(mealPlanId, STATUS_GENERATING, now)
        log.info("Meal plan $mealPlanId set to GENERATING for user $userId")

        generationScope.launch {
            generateAsync(mealPlanId, userId, record.plan.preferencesJson)
        }

        return GenerateMealPlanResponse(
            uuid = mealPlanId.toString(),
            status = STATUS_GENERATING,
            updatedAt = now.toEpochMilliseconds()
        )
    }

    // ── Internal generation pipeline ──────────────────────────────────────────

    private suspend fun generateAsync(planId: UUID, userId: UUID, preferencesJson: String) {
        try {
            val prefs = parsePreferences(preferencesJson)

            val candidateIds = syncRepository.findCandidateRecipeIds(
                userId = userId,
                recipeSource = prefs.recipeSource,
                dietaryRestrictionTags = prefs.dietaryRestrictions.filter { it != "NONE" },
                maxPrepTimeMinutes = prefs.maxPrepTimeMinutes
            )

            val rankingMetadata = syncRepository.findRecipeRankingMetadata(candidateIds.toSet())
            val recentlyUsedElsewhere = syncRepository.findRecentlyUsedRecipeIds(
                userId = userId,
                excludePlanId = planId,
                limit = RECENT_PLAN_HISTORY_LIMIT
            )

            val days = assignRecipesToDays(candidateIds, prefs, rankingMetadata, recentlyUsedElsewhere)

            val now = Clock.System.now()
            syncRepository.replaceMealPlanDays(planId, days)
            syncRepository.updateMealPlanStatus(planId, STATUS_READY, now)
            log.info("Meal plan $planId generation complete: ${days.size} days assigned for user $userId")
        } catch (ex: Exception) {
            log.error("Meal plan $planId generation failed for user $userId", ex)
            // Leave status as GENERATING so the client can detect the stall and retry if needed.
        }
    }

    /**
     * Assigns candidate recipe UUIDs to plan days according to [prefs].
     *
     * Variety rules:
     * - HIGH: no recipe repeats until every candidate has been used once, then round-robins.
     *   A plan is only ever partially filled when [candidateIds] itself is empty — never merely
     *   for lack of variety headroom.
     * - MEDIUM: a recipe may reappear only after a 3-day gap; cycles when all are blocked.
     * - LOW: free repetition — cycles round-robin through the candidate list.
     *
     * Batch-cooking: when [prefs.batchCooking] is true, odd-indexed days reuse the previous
     * day's assignment (pairs intentionally share a recipe for batch-prep efficiency).
     *
     * Three additional signals bias which candidate [pickFrom] sees first, but never override
     * the variety rules above or leave a slot unfilled that would otherwise be filled:
     * - [prefs.leftoverFriendly]: candidates whose `servings` exceeds [prefs.servingsPerMeal]
     *   are shuffled to the front, so recipes that actually produce leftovers are favored early.
     * - Dominant-ingredient repetition: each day's pick is soft-deprioritized (not excluded) if
     *   its [RecipeRankingMetadata.dominantCategory] matches the immediately-previous day's pick
     *   for the same meal slot (dinner/lunch tracked independently). Only applies to MEDIUM/HIGH —
     *   see [rankForDay] for why LOW is exempt.
     * - [recentlyUsedElsewhere]: candidates used in the user's recent past `READY` plans are
     *   soft-deprioritized, so a new plan favors recipes the user hasn't seen recently. Also
     *   MEDIUM/HIGH only, same reason.
     */
    internal fun assignRecipesToDays(
        candidateIds: List<UUID>,
        prefs: MealPlanPreferences,
        rankingMetadata: Map<UUID, RecipeRankingMetadata> = emptyMap(),
        recentlyUsedElsewhere: Set<UUID> = emptySet()
    ): List<SyncMealPlanDayDto> {
        if (candidateIds.isEmpty()) return buildEmptyDays(prefs.planLengthDays)

        val shuffled = leftoverAwareShuffle(candidateIds, prefs, rankingMetadata)
        val days = mutableListOf<SyncMealPlanDayDto>()
        val dinnerHistory = ArrayDeque<UUID>()
        val lunchHistory = ArrayDeque<UUID>()
        var previousDinnerCategory: String? = null
        var previousLunchCategory: String? = null

        for (dayIndex in 0 until prefs.planLengthDays) {
            val dinnerRecipeId: UUID?
            val lunchRecipeId: UUID?

            if (prefs.batchCooking && dayIndex > 0 && dayIndex % 2 == 1) {
                // Batch day: reuse the previous day's recipe for intentional batch prep
                dinnerRecipeId = days.last().dinnerRecipeId?.let { UUID.fromString(it) }
                lunchRecipeId = days.last().lunchRecipeId?.let { UUID.fromString(it) }
            } else {
                val dinnerRanked = rankForDay(shuffled, prefs.varietyPreference, recentlyUsedElsewhere, previousDinnerCategory, rankingMetadata)
                dinnerRecipeId = pickFrom(dinnerRanked, dinnerHistory, prefs.varietyPreference)
                dinnerRecipeId?.let {
                    dinnerHistory.addLast(it)
                    previousDinnerCategory = rankingMetadata[it]?.dominantCategory
                }

                lunchRecipeId = if (prefs.mealType == MealType.DINNER_AND_LUNCH) {
                    val lunchRanked = rankForDay(shuffled, prefs.varietyPreference, recentlyUsedElsewhere, previousLunchCategory, rankingMetadata)
                    pickFrom(lunchRanked, lunchHistory, prefs.varietyPreference).also {
                        it?.let { id ->
                            lunchHistory.addLast(id)
                            previousLunchCategory = rankingMetadata[id]?.dominantCategory
                        }
                    }
                } else null
            }

            days += SyncMealPlanDayDto(
                uuid = UUID.randomUUID().toString(),
                dayIndex = dayIndex,
                dinnerRecipeId = dinnerRecipeId?.toString(),
                lunchRecipeId = lunchRecipeId?.toString()
            )
        }

        return days
    }

    /**
     * When [MealPlanPreferences.leftoverFriendly] is true, favors candidates whose `servings`
     * exceeds [MealPlanPreferences.servingsPerMeal] by shuffling them ahead of the rest; otherwise
     * a plain shuffle. Ordering only — never excludes a candidate.
     */
    private fun leftoverAwareShuffle(
        candidateIds: List<UUID>,
        prefs: MealPlanPreferences,
        rankingMetadata: Map<UUID, RecipeRankingMetadata>
    ): List<UUID> {
        if (!prefs.leftoverFriendly) return candidateIds.shuffled()

        val (leftoverFavoring, rest) = candidateIds.partition {
            (rankingMetadata[it]?.servings ?: 0) > prefs.servingsPerMeal
        }
        return leftoverFavoring.shuffled() + rest.shuffled()
    }

    /**
     * Stably re-sorts [candidates] ahead of a single day's [pickFrom] call: candidates recently
     * used in the user's other plans sort last, and (within that) candidates sharing
     * [previousCategory] with the prior day's pick for this meal slot sort last. Ordering only —
     * [pickFrom]'s own variety/fallback logic is unaffected.
     *
     * Skipped for [VarietyPreference.LOW]: its `candidates[history.size % candidates.size]`
     * round-robin walks every position in the list over time, so re-sorting per day doesn't bias
     * outcomes the way it does for MEDIUM/HIGH's "try preferred first" — it can even land on the
     * *least*-preferred position for some day/candidate-count combinations. LOW's contract is pure
     * free repetition; these signals only apply where there's a "try preferred first" step to hook.
     */
    private fun rankForDay(
        candidates: List<UUID>,
        variety: VarietyPreference,
        recentlyUsedElsewhere: Set<UUID>,
        previousCategory: String?,
        rankingMetadata: Map<UUID, RecipeRankingMetadata>
    ): List<UUID> {
        if (variety == VarietyPreference.LOW) return candidates
        return candidates.sortedWith(
            compareBy(
                { it in recentlyUsedElsewhere },
                { previousCategory != null && rankingMetadata[it]?.dominantCategory == previousCategory }
            )
        )
    }

    /**
     * Picks the next recipe from [candidates] without consuming the list, using [history]
     * to track previously assigned recipes.
     *
     * - HIGH: must not appear anywhere in history; once all candidates are used, falls back to
     *   round-robin via `history.size % candidates.size` — always succeeds when candidates is non-empty.
     * - MEDIUM: must not appear in the last 3 history entries; cycles when all are blocked.
     * - LOW: round-robin via `history.size % candidates.size` — always succeeds.
     */
    private fun pickFrom(
        candidates: List<UUID>,
        history: ArrayDeque<UUID>,
        variety: VarietyPreference
    ): UUID? {
        if (candidates.isEmpty()) return null
        return when (variety) {
            VarietyPreference.LOW ->
                candidates[history.size % candidates.size]
            VarietyPreference.MEDIUM -> {
                val recent = history.takeLast(3).toSet()
                candidates.firstOrNull { it !in recent }
                    ?: candidates[history.size % candidates.size]
            }
            VarietyPreference.HIGH -> {
                val used = history.toSet()
                candidates.firstOrNull { it !in used }
                    ?: candidates[history.size % candidates.size]
            }
        }
    }

    private fun buildEmptyDays(count: Int): List<SyncMealPlanDayDto> =
        (0 until count).map { idx ->
            SyncMealPlanDayDto(
                uuid = UUID.randomUUID().toString(),
                dayIndex = idx,
                dinnerRecipeId = null,
                lunchRecipeId = null
            )
        }

    // ── Preferences parsing ───────────────────────────────────────────────────

    internal fun parsePreferences(json: String): MealPlanPreferences {
        val obj: JsonObject = lenientJson.parseToJsonElement(json).jsonObject

        val planLengthDays = obj["planLengthDays"]?.jsonPrimitive?.intOrNull ?: 7
        val mealType = obj["mealType"]?.jsonPrimitive?.content
            ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
            ?: MealType.DINNER
        val dietaryRestrictions = obj["dietaryRestrictions"]?.jsonArray
            ?.map { it.jsonPrimitive.content }
            ?: emptyList()
        val recipeSource = obj["recipeSource"]?.jsonPrimitive?.content ?: "INCLUDE_PUBLIC"
        val maxPrepTimeMinutes = obj["maxPrepTimeMinutes"]?.jsonPrimitive?.intOrNull
        val servingsPerMeal = obj["servingsPerMeal"]?.jsonPrimitive?.intOrNull ?: 2
        val batchCooking = obj["batchCooking"]?.jsonPrimitive?.booleanOrNull ?: false
        val leftoverFriendly = obj["leftoverFriendly"]?.jsonPrimitive?.booleanOrNull ?: false
        val varietyPreference = obj["varietyPreference"]?.jsonPrimitive?.content
            ?.let { runCatching { VarietyPreference.valueOf(it) }.getOrNull() }
            ?: VarietyPreference.HIGH

        return MealPlanPreferences(
            planLengthDays = planLengthDays.coerceAtLeast(1),
            mealType = mealType,
            dietaryRestrictions = dietaryRestrictions,
            recipeSource = recipeSource,
            maxPrepTimeMinutes = maxPrepTimeMinutes?.takeIf { it > 0 },
            servingsPerMeal = servingsPerMeal,
            batchCooking = batchCooking,
            leftoverFriendly = leftoverFriendly,
            varietyPreference = varietyPreference
        )
    }

    companion object {
        const val STATUS_GENERATING = "GENERATING"
        const val STATUS_READY = "READY"

        /** How many of the user's most recent READY plans count toward [SyncRepository.findRecentlyUsedRecipeIds]. */
        internal const val RECENT_PLAN_HISTORY_LIMIT = 3

        private val lenientJson = Json { ignoreUnknownKeys = true }
    }
}

// ── Domain value types (package-private) ─────────────────────────────────────

data class MealPlanPreferences(
    val planLengthDays: Int,
    val mealType: MealType,
    val dietaryRestrictions: List<String>,
    val recipeSource: String,
    val maxPrepTimeMinutes: Int?,
    val servingsPerMeal: Int,
    val batchCooking: Boolean,
    val leftoverFriendly: Boolean,
    val varietyPreference: VarietyPreference
)

enum class MealType { DINNER, DINNER_AND_LUNCH }
enum class VarietyPreference { HIGH, MEDIUM, LOW }
