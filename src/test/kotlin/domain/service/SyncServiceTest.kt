package domain.service

import com.tenmilelabs.application.dto.SyncPushRequest
import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.application.dto.SyncRecipeIngredient
import com.tenmilelabs.application.dto.SyncRecipeStep
import com.tenmilelabs.application.dto.ConflictReasons
import com.tenmilelabs.application.dto.SyncErrors
import com.tenmilelabs.application.dto.SyncMealPlanDayDto
import com.tenmilelabs.application.dto.SyncMealPlanDto
import com.tenmilelabs.domain.service.RecipeDetailResult
import com.tenmilelabs.domain.service.SyncService
import com.tenmilelabs.infrastructure.database.FakeSyncRepository
import com.tenmilelabs.infrastructure.database.FakeUserPreferencesRepository
import io.ktor.server.testing.TestApplicationBuilder
import io.ktor.server.testing.testApplication
import io.ktor.util.logging.KtorSimpleLogger
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncServiceTest {
    @Test
    fun pushAcceptsNewRecipe() = withService { service, repo ->
        val userId = UUID.randomUUID()
        val recipe = sampleRecipe(
            uuid = UUID.randomUUID(),
            creatorId = userId,
            updatedAt = 1000L,
            ingredientId = repo.seedIngredient()
        )

        val response = service.pushRecipes(userId, SyncPushRequest(listOf(recipe)))

        assertEquals(1, response.accepted.size)
        assertEquals(0, response.conflicts.size)
        assertEquals(0, response.errors.size)
        assertTrue(repo.getRecipe(UUID.fromString(recipe.uuid)) != null)
    }

    @Test
    fun pushReturnsConflictWhenServerIsNewer() = withService { service, repo ->
        val userId = UUID.randomUUID()
        val recipeId = UUID.randomUUID()
        val ingredientId = repo.seedIngredient()
        repo.seedRecipe(
            sampleRecipe(
                uuid = recipeId,
                creatorId = userId,
                updatedAt = 1000L,
                ingredientId = ingredientId
            ),
            serverUpdatedAtMillis = 2000L
        )

        val staleLocal = sampleRecipe(
            uuid = recipeId,
            creatorId = userId,
            updatedAt = 1500L,
            ingredientId = ingredientId
        )

        val response = service.pushRecipes(userId, SyncPushRequest(listOf(staleLocal)))
        assertEquals(0, response.accepted.size)
        assertEquals(1, response.conflicts.size)
        assertEquals(ConflictReasons.SERVER_NEWER, response.conflicts.first().reason)
    }

    @Test
    fun pushReturnsErrorForInvalidCreator() = withService { service, repo ->
        val userId = UUID.randomUUID()
        val recipe = sampleRecipe(
            uuid = UUID.randomUUID(),
            creatorId = UUID.randomUUID(),
            updatedAt = 1000L,
            ingredientId = repo.seedIngredient()
        )

        val response = service.pushRecipes(userId, SyncPushRequest(listOf(recipe)))
        assertEquals(1, response.errors.size)
        assertEquals(SyncErrors.CREATOR_MISMATCH, response.errors.first().reason)
        assertEquals(SyncErrors.CREATOR_MISMATCH.message, response.errors.first().message)
    }

    @Test
    fun pullPaginatesAndReturnsOnlyDeltas() = withService { service, repo ->
        val userId = UUID.randomUUID()
        val ingredientId = repo.seedIngredient()

        val ownA = sampleRecipe(UUID.randomUUID(), userId, 1000L, ingredientId)
        val ownB = sampleRecipe(UUID.randomUUID(), userId, 2000L, ingredientId)
        val publicRecipe = sampleRecipe(UUID.randomUUID(), UUID.randomUUID(), 3000L, ingredientId, "PUBLIC")
        val privateForeign = sampleRecipe(UUID.randomUUID(), UUID.randomUUID(), 4000L, ingredientId, "PRIVATE")

        repo.seedRecipe(ownA, 1100L)
        repo.seedRecipe(ownB, 2200L)
        repo.seedRecipe(publicRecipe, 3300L)
        repo.seedRecipe(privateForeign, 4400L)

        val response = service.pullRecipes(userId = userId, sinceMillis = 1000L, limit = 2)

        assertEquals(2, response.recipes.size)
        assertTrue(response.hasMore)
        assertFalse(response.recipes.any { it.uuid == privateForeign.uuid })
        val pageCreatorIds = response.recipes.map { it.creatorId }.toSet()
        val returnedCreatorIds = response.creators.map { it.uuid }.toSet()
        assertEquals(pageCreatorIds, returnedCreatorIds)
    }

    /**
     * Regression test for the frozen-cursor bug: several recipes sharing one exact
     * `serverUpdatedAtMillis` (e.g. a batch push landing in the same millisecond) must not be
     * split across a page boundary — see [SyncService.fetchStablePage]. When earlier, distinct
     * timestamps exist, the tied group is deferred whole to the next page rather than cut.
     */
    @Test
    fun pullDefersEntireTiedGroupInsteadOfSplittingIt() = withService { service, repo ->
        val userId = UUID.randomUUID()
        val ingredientId = repo.seedIngredient()

        val early = sampleRecipe(UUID.randomUUID(), userId, 500L, ingredientId)
        val tiedA = sampleRecipe(UUID.randomUUID(), userId, 1000L, ingredientId)
        val tiedB = sampleRecipe(UUID.randomUUID(), userId, 1000L, ingredientId)
        val tiedC = sampleRecipe(UUID.randomUUID(), userId, 1000L, ingredientId)
        val later = sampleRecipe(UUID.randomUUID(), userId, 2000L, ingredientId)
        repo.seedRecipe(early, 500L)
        repo.seedRecipe(tiedA, 1000L)
        repo.seedRecipe(tiedB, 1000L)
        repo.seedRecipe(tiedC, 1000L)
        repo.seedRecipe(later, 2000L)

        val allIds = setOf(early, tiedA, tiedB, tiedC, later).map { it.uuid }.toSet()
        val seen = mutableSetOf<String>()
        var since = 0L
        var hasMore = true
        var pages = 0

        while (hasMore) {
            pages++
            assertTrue(pages <= 5, "pagination did not terminate within a bounded number of pages")

            val response = service.pullRecipes(userId = userId, sinceMillis = since, limit = 2)
            val returnedIds = response.recipes.map { it.uuid }.toSet()

            assertTrue(returnedIds.isNotEmpty(), "page $pages returned no recipes while hasMore was true")
            val repeated = seen.intersect(returnedIds)
            assertTrue(repeated.isEmpty(), "page $pages repeated recipes already returned: $repeated")

            seen += returnedIds
            since = response.serverTimestamp
            hasMore = response.hasMore
        }

        assertEquals(allIds, seen)
        assertEquals(3, pages) // [early] | [tiedA, tiedB, tiedC] (kept whole, past limit=2) | [later]
    }

    /**
     * Same regression, but the tie has no earlier material to defer to at all — every recipe
     * across two distinct timestamps ties with its own group, exercising the widen-and-return-
     * whole-group path in [SyncService.fetchStablePage] on both pulls.
     */
    @Test
    fun pullAdvancesAcrossConsecutiveTiedGroupsExceedingLimit() = withService { service, repo ->
        val userId = UUID.randomUUID()
        val ingredientId = repo.seedIngredient()

        val groupA = (1..4).map { sampleRecipe(UUID.randomUUID(), userId, 1000L, ingredientId) }
        val groupB = (1..4).map { sampleRecipe(UUID.randomUUID(), userId, 2000L, ingredientId) }
        (groupA + groupB).forEachIndexed { index, recipe ->
            repo.seedRecipe(recipe, if (index < 4) 1000L else 2000L)
        }

        val allIds = (groupA + groupB).map { it.uuid }.toSet()
        val seen = mutableSetOf<String>()
        var since = 0L
        var hasMore = true
        var pages = 0

        while (hasMore) {
            pages++
            assertTrue(pages <= 5, "pagination did not terminate within a bounded number of pages")

            val response = service.pullRecipes(userId = userId, sinceMillis = since, limit = 3)
            val returnedIds = response.recipes.map { it.uuid }.toSet()

            assertTrue(returnedIds.isNotEmpty(), "page $pages returned no recipes while hasMore was true")
            val repeated = seen.intersect(returnedIds)
            assertTrue(repeated.isEmpty(), "page $pages repeated recipes already returned: $repeated")
            // Each tied group of 4 must come back intact in one page, never split by `limit`=3.
            assertTrue(returnedIds.size == 4, "page $pages split a 4-way tied group: got ${returnedIds.size}")

            seen += returnedIds
            since = response.serverTimestamp
            hasMore = response.hasMore
        }

        assertEquals(allIds, seen)
        assertEquals(2, pages)
    }

    @Test
    fun pullReturnsUniqueCreatorsPerPage() = withService { service, repo ->
        val userId = UUID.randomUUID()
        val ingredientId = repo.seedIngredient()

        val ownA = sampleRecipe(UUID.randomUUID(), userId, 1000L, ingredientId)
        val ownB = sampleRecipe(UUID.randomUUID(), userId, 2000L, ingredientId)
        repo.seedRecipe(ownA, 1100L)
        repo.seedRecipe(ownB, 2200L)

        val response = service.pullRecipes(userId = userId, sinceMillis = 0L, limit = 10)

        assertEquals(2, response.recipes.size)
        assertEquals(1, response.creators.size)
        assertEquals(userId.toString(), response.creators.first().uuid)
    }

    // ── Referential completeness tests ───────────────────────────────────────

    /**
     * Example 1 (unit): A tag whose server_updated_at predates `since` must still
     * appear in response.tags when it is referenced by a recipe in the returned page
     * (gap clause of the union). Without this, recipe_tags.tagId FK insert would fail.
     */
    @Test
    fun pullIncludesGapTagReferencedByReturnedRecipe() = withService { service, repo ->
        val userId = UUID.randomUUID()
        val ingredientId = repo.seedIngredient()
        val tagId = repo.seedTag(serverUpdatedAt = 500L) // predates since=1000

        val recipe = sampleRecipe(
            uuid = UUID.randomUUID(),
            creatorId = userId,
            updatedAt = 2000L,
            ingredientId = ingredientId
        ).copy(tagIds = listOf(tagId.toString()))
        repo.seedRecipe(recipe, serverUpdatedAtMillis = 2000L)

        val response = service.pullRecipes(userId = userId, sinceMillis = 1000L, limit = 10)

        assertEquals(1, response.recipes.size)
        assertEquals(1, response.tags.size)
        assertEquals(tagId.toString(), response.tags.first().uuid)
    }

    /**
     * An ingredient updated after `since` but not referenced by any recipe in the
     * current page must still appear in response.ingredients via the delta clause.
     * Ensures the client receives all catalogue updates, not just referenced ones.
     */
    @Test
    fun pullIncludesDeltaIngredientNotReferencedByCurrentPage() = withService { service, repo ->
        val userId = UUID.randomUUID()
        val referencedIngredientId = repo.seedIngredient(serverUpdatedAt = 0L)
        val deltaOnlyIngredientId = repo.seedIngredient(serverUpdatedAt = 1500L) // after since=1000, not in recipe

        val recipe = sampleRecipe(
            uuid = UUID.randomUUID(),
            creatorId = userId,
            updatedAt = 2000L,
            ingredientId = referencedIngredientId
        )
        repo.seedRecipe(recipe, serverUpdatedAtMillis = 2000L)

        val response = service.pullRecipes(userId = userId, sinceMillis = 1000L, limit = 10)

        val returnedIds = response.ingredients.map { it.uuid }.toSet()
        assertTrue(returnedIds.contains(referencedIngredientId.toString())) // gap
        assertTrue(returnedIds.contains(deltaOnlyIngredientId.toString()))  // delta
    }

    /**
     * Example 2 (unit): When a push produces a conflict, response.referenceData must
     * include every entity referenced by the conflict's serverVersion so the client
     * can upsert it without a separate pull. Without this, recipe_ingredients.ingredientId
     * FK insert would fail on the client side.
     */
    @Test
    fun pushConflictReferenceDataIncludesIngredientFromServerVersion() = withService { service, repo ->
        val userId = UUID.randomUUID()
        val recipeId = UUID.randomUUID()
        val serverIngredientId = repo.seedIngredient(serverUpdatedAt = 100L)

        // Server has a newer version of this recipe
        val serverRecipe = sampleRecipe(
            uuid = recipeId,
            creatorId = userId,
            updatedAt = 5000L,
            ingredientId = serverIngredientId
        )
        repo.seedRecipe(serverRecipe, serverUpdatedAtMillis = 5000L)

        // Client pushes a stale version — triggers conflict
        val staleClientRecipe = sampleRecipe(
            uuid = recipeId,
            creatorId = userId,
            updatedAt = 1000L,
            ingredientId = serverIngredientId
        )

        val response = service.pushRecipes(userId, SyncPushRequest(listOf(staleClientRecipe)))

        assertEquals(1, response.conflicts.size)
        assertEquals(ConflictReasons.SERVER_NEWER, response.conflicts.first().reason)
        val referenceIngredientIds = response.referenceData.ingredients.map { it.uuid }.toSet()
        assertTrue(referenceIngredientIds.contains(serverIngredientId.toString()))
    }

    @Test
    fun pushReturnsErrorForMalformedTagId() = withService { service, repo ->
        val userId = UUID.randomUUID()
        val ingredientId = repo.seedIngredient()
        val recipe = sampleRecipe(
            uuid = UUID.randomUUID(),
            creatorId = userId,
            updatedAt = 1000L,
            ingredientId = ingredientId
        ).copy(tagIds = listOf("not-a-uuid"))

        val response = service.pushRecipes(userId, SyncPushRequest(listOf(recipe)))

        assertEquals(0, response.accepted.size)
        assertEquals(1, response.errors.size)
        assertEquals(SyncErrors.INVALID_TAG, response.errors.first().reason)
        assertEquals(SyncErrors.INVALID_TAG.message, response.errors.first().message)
    }

    // ── getRecipeDetail (ChefAI#186) ─────────────────────────────────────────

    @Test
    fun getRecipeDetailReturnsFoundForAnonymousCallerAndPublicRecipe() = withService { service, repo ->
        val recipeId = UUID.randomUUID()
        val recipe = sampleRecipe(recipeId, UUID.randomUUID(), 1000L, repo.seedIngredient(), "PUBLIC")
        repo.seedRecipe(recipe, serverUpdatedAtMillis = 1000L)

        val result = service.getRecipeDetail(userId = null, recipeId = recipeId)

        assertTrue(result is RecipeDetailResult.Found)
        assertEquals(recipeId.toString(), (result as RecipeDetailResult.Found).recipe.uuid)
    }

    @Test
    fun getRecipeDetailReturnsNotFoundForAnonymousCallerAndPrivateRecipe() = withService { service, repo ->
        val recipeId = UUID.randomUUID()
        val recipe = sampleRecipe(recipeId, UUID.randomUUID(), 1000L, repo.seedIngredient(), "PRIVATE")
        repo.seedRecipe(recipe, serverUpdatedAtMillis = 1000L)

        val result = service.getRecipeDetail(userId = null, recipeId = recipeId)

        assertEquals(RecipeDetailResult.NotFound, result)
    }

    @Test
    fun getRecipeDetailReturnsFoundForOwnerOfPrivateRecipe() = withService { service, repo ->
        val ownerId = UUID.randomUUID()
        val recipeId = UUID.randomUUID()
        val recipe = sampleRecipe(recipeId, ownerId, 1000L, repo.seedIngredient(), "PRIVATE")
        repo.seedRecipe(recipe, serverUpdatedAtMillis = 1000L)

        val result = service.getRecipeDetail(userId = ownerId, recipeId = recipeId)

        assertTrue(result is RecipeDetailResult.Found)
    }

    @Test
    fun getRecipeDetailReturnsNotFoundForNonOwnerOfPrivateRecipe() = withService { service, repo ->
        val recipeId = UUID.randomUUID()
        val recipe = sampleRecipe(recipeId, UUID.randomUUID(), 1000L, repo.seedIngredient(), "PRIVATE")
        repo.seedRecipe(recipe, serverUpdatedAtMillis = 1000L)

        val result = service.getRecipeDetail(userId = UUID.randomUUID(), recipeId = recipeId)

        assertEquals(RecipeDetailResult.NotFound, result)
    }

    @Test
    fun getRecipeDetailReturnsNotFoundForNonexistentRecipe() = withService { service, _ ->
        val result = service.getRecipeDetail(userId = null, recipeId = UUID.randomUUID())

        assertEquals(RecipeDetailResult.NotFound, result)
    }

    @Test
    fun getRecipeDetailReturnsNotFoundForSoftDeletedRecipeEvenWhenPublicAndOwned() = withService { service, repo ->
        val ownerId = UUID.randomUUID()
        val recipeId = UUID.randomUUID()
        val recipe = sampleRecipe(recipeId, ownerId, 1000L, repo.seedIngredient(), "PUBLIC")
            .copy(deletedAt = 1500L)
        repo.seedRecipe(recipe, serverUpdatedAtMillis = 1500L)

        val result = service.getRecipeDetail(userId = ownerId, recipeId = recipeId)

        assertEquals(RecipeDetailResult.NotFound, result)
    }

    @Test
    fun getRecipeDetailReferenceDataMatchesTheRecipesOwnDependencies() = withService { service, repo ->
        val recipeId = UUID.randomUUID()
        val tagId = repo.seedTag(serverUpdatedAt = 50L)
        val labelId = repo.seedLabel(serverUpdatedAt = 50L)
        val ingredientId = repo.seedIngredient(serverUpdatedAt = 50L)
        val recipe = sampleRecipe(recipeId, UUID.randomUUID(), 1000L, ingredientId, "PUBLIC")
            .copy(tagIds = listOf(tagId.toString()), labelIds = listOf(labelId.toString()))
        repo.seedRecipe(recipe, serverUpdatedAtMillis = 1000L)

        val result = service.getRecipeDetail(userId = null, recipeId = recipeId) as RecipeDetailResult.Found

        assertEquals(setOf(ingredientId.toString()), result.referenceData.ingredients.map { it.uuid }.toSet())
        assertEquals(setOf(tagId.toString()), result.referenceData.tags.map { it.uuid }.toSet())
        assertEquals(setOf(labelId.toString()), result.referenceData.labels.map { it.uuid }.toSet())
    }

    @Test
    fun getRecipeDetailCreatorsContainsTheRecipesAuthor() = withService { service, repo ->
        val creatorId = repo.seedUser()
        val recipeId = UUID.randomUUID()
        val recipe = sampleRecipe(recipeId, creatorId, 1000L, repo.seedIngredient(), "PUBLIC")
        repo.seedRecipe(recipe, serverUpdatedAtMillis = 1000L)

        val result = service.getRecipeDetail(userId = null, recipeId = recipeId) as RecipeDetailResult.Found

        assertEquals(setOf(creatorId.toString()), result.creators.map { it.uuid }.toSet())
    }

    // ── getRecipeDetails (batched, for stateless meal-plan generation) ────────

    @Test
    fun getRecipeDetailsMergesAndDeduplicatesSharedReferenceDataAndCreators() = withService { service, repo ->
        val creatorId = repo.seedUser()
        val sharedTagId = repo.seedTag(serverUpdatedAt = 50L)
        val sharedLabelId = repo.seedLabel(serverUpdatedAt = 50L)
        val sharedIngredientId = repo.seedIngredient(serverUpdatedAt = 50L)

        val firstRecipeId = UUID.randomUUID()
        val firstRecipe = sampleRecipe(firstRecipeId, creatorId, 1000L, sharedIngredientId, "PUBLIC")
            .copy(tagIds = listOf(sharedTagId.toString()), labelIds = listOf(sharedLabelId.toString()))
        repo.seedRecipe(firstRecipe, serverUpdatedAtMillis = 1000L)

        val secondRecipeId = UUID.randomUUID()
        val secondRecipe = sampleRecipe(secondRecipeId, creatorId, 1000L, sharedIngredientId, "PUBLIC")
            .copy(tagIds = listOf(sharedTagId.toString()), labelIds = listOf(sharedLabelId.toString()))
        repo.seedRecipe(secondRecipe, serverUpdatedAtMillis = 1000L)

        val bundle = service.getRecipeDetails(userId = null, recipeIds = setOf(firstRecipeId, secondRecipeId))

        assertEquals(setOf(firstRecipeId.toString(), secondRecipeId.toString()), bundle.recipes.map { it.uuid }.toSet())
        assertEquals(1, bundle.referenceData.ingredients.size)
        assertEquals(1, bundle.referenceData.tags.size)
        assertEquals(1, bundle.referenceData.labels.size)
        assertEquals(1, bundle.creators.size)
    }

    @Test
    fun getRecipeDetailsOmitsIdsThatResolveToNotFound() = withService { service, repo ->
        val recipeId = UUID.randomUUID()
        val recipe = sampleRecipe(recipeId, UUID.randomUUID(), 1000L, repo.seedIngredient(), "PUBLIC")
        repo.seedRecipe(recipe, serverUpdatedAtMillis = 1000L)

        val bundle = service.getRecipeDetails(userId = null, recipeIds = setOf(recipeId, UUID.randomUUID()))

        assertEquals(listOf(recipeId.toString()), bundle.recipes.map { it.uuid })
    }

    @Test
    fun pushingAMealPlanUpdatesStoredUserPreferences() {
        testApplication {
            val syncRepo = FakeSyncRepository()
            val prefsRepo = FakeUserPreferencesRepository()
            val service = SyncService(syncRepo, KtorSimpleLogger("SyncServiceTest"), prefsRepo)
            val userId = UUID.randomUUID()
            val planId = UUID.randomUUID()
            val preferencesJson = """{"planLengthDays":5,"mealType":"DINNER","recipeSource":"COLLECTION_ONLY"}"""

            val response = service.pushRecipes(
                userId,
                SyncPushRequest(
                    recipes = emptyList(),
                    mealPlans = listOf(buildMealPlan(planId, updatedAt = 1000L, ownerId = userId, preferencesJson = preferencesJson))
                )
            )

            assertEquals(1, response.mealPlans.accepted.size)
            assertEquals(preferencesJson, prefsRepo.getUserPreferences(userId))
        }
    }

    @Test
    fun conflictedMealPlanPushDoesNotUpdateStoredUserPreferences() {
        testApplication {
            val syncRepo = FakeSyncRepository()
            val prefsRepo = FakeUserPreferencesRepository()
            val service = SyncService(syncRepo, KtorSimpleLogger("SyncServiceTest"), prefsRepo)
            val userId = UUID.randomUUID()
            val planId = UUID.randomUUID()

            // Server already has a newer version of this plan, owned by the same user pushing the stale update.
            syncRepo.seedMealPlan(buildMealPlan(planId, updatedAt = 9000L, ownerId = userId), serverUpdatedAtMillis = 9000L)

            val staleJson = """{"planLengthDays":1,"mealType":"DINNER","recipeSource":"INCLUDE_PUBLIC"}"""
            val response = service.pushRecipes(
                userId,
                SyncPushRequest(
                    recipes = emptyList(),
                    mealPlans = listOf(buildMealPlan(planId, updatedAt = 1000L, ownerId = userId, preferencesJson = staleJson))
                )
            )

            assertEquals(1, response.mealPlans.conflicts.size)
            assertEquals(null, prefsRepo.getUserPreferences(userId))
        }
    }

    // ── Households: shared meal plans (backend prompt §6.1–§6.3) ────────────────

    @Test
    fun sharedPlanPushByANonCreatorMemberIsAccepted() = withService { service, repo ->
        val householdId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        repo.seedActiveHousehold(ownerId, householdId)
        repo.seedActiveHousehold(memberId, householdId)
        val planId = UUID.randomUUID()
        repo.seedMealPlan(
            buildMealPlan(planId, updatedAt = 1000L, ownerId = ownerId).copy(householdId = householdId.toString()),
            serverUpdatedAtMillis = 1000L
        )

        val response = service.pushRecipes(
            memberId,
            SyncPushRequest(
                recipes = emptyList(),
                mealPlans = listOf(
                    buildMealPlan(planId, updatedAt = 2000L, ownerId = ownerId).copy(householdId = householdId.toString())
                )
            )
        )

        assertEquals(1, response.mealPlans.accepted.size)
    }

    @Test
    fun pushOfANewPlanClaimingAHouseholdTheCallerIsNotAMemberOfIsRejected() = withService { service, repo ->
        val userId = UUID.randomUUID()
        val foreignHouseholdId = UUID.randomUUID()
        val planId = UUID.randomUUID()

        val response = service.pushRecipes(
            userId,
            SyncPushRequest(
                recipes = emptyList(),
                mealPlans = listOf(
                    buildMealPlan(planId, updatedAt = 1000L, ownerId = userId).copy(householdId = foreignHouseholdId.toString())
                )
            )
        )

        assertEquals(1, response.mealPlans.errors.size)
        assertEquals(SyncErrors.INVALID_HOUSEHOLD, response.mealPlans.errors.single().reason)
    }

    @Test
    fun pushOfANewPlanWithAnOwnerIdNotMatchingTheCallerIsRejected() = withService { service, repo ->
        val userId = UUID.randomUUID()
        val someoneElse = UUID.randomUUID()
        val planId = UUID.randomUUID()

        val response = service.pushRecipes(
            userId,
            SyncPushRequest(
                recipes = emptyList(),
                mealPlans = listOf(buildMealPlan(planId, updatedAt = 1000L, ownerId = someoneElse))
            )
        )

        assertEquals(1, response.mealPlans.errors.size)
        assertEquals(SyncErrors.OWNER_MISMATCH, response.mealPlans.errors.single().reason)
    }

    @Test
    fun pushOfAnExistingPlanTheCallerCannotAccessIsRejected() = withService { service, repo ->
        val ownerId = UUID.randomUUID()
        val strangerId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        repo.seedMealPlan(buildMealPlan(planId, updatedAt = 1000L, ownerId = ownerId), serverUpdatedAtMillis = 1000L)

        val response = service.pushRecipes(
            strangerId,
            SyncPushRequest(
                recipes = emptyList(),
                mealPlans = listOf(buildMealPlan(planId, updatedAt = 2000L, ownerId = ownerId))
            )
        )

        assertEquals(1, response.mealPlans.errors.size)
        assertEquals(SyncErrors.MEAL_PLAN_NOT_ACCESSIBLE, response.mealPlans.errors.single().reason)
    }

    @Test
    fun sharedPlanPushReferencingARecipeTheCallerCannotAccessIsRejected() = withService { service, repo ->
        // Closes a leak the recipe gap clause would otherwise open: without this check, any
        // household member could reference an unrelated private recipe (owned by a third party,
        // not the pusher) in a shared plan's day and have it leaked to the whole household.
        val householdId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        repo.seedActiveHousehold(memberId, householdId)
        val strangerId = UUID.randomUUID()
        val ingredientId = repo.seedIngredient()
        val strangersPrivateRecipe = sampleRecipe(UUID.randomUUID(), strangerId, updatedAt = 100L, ingredientId = ingredientId)
        repo.seedRecipe(strangersPrivateRecipe, serverUpdatedAtMillis = 100L)

        val planId = UUID.randomUUID()
        val plan = buildMealPlan(planId, updatedAt = 1000L, ownerId = memberId).copy(
            householdId = householdId.toString(),
            days = listOf(
                SyncMealPlanDayDto(
                    uuid = UUID.randomUUID().toString(),
                    dayIndex = 0,
                    dinnerRecipeId = strangersPrivateRecipe.uuid,
                    lunchRecipeId = null
                )
            )
        )

        val response = service.pushRecipes(memberId, SyncPushRequest(recipes = emptyList(), mealPlans = listOf(plan)))

        assertEquals(1, response.mealPlans.errors.size)
        assertEquals(SyncErrors.MEAL_PLAN_RECIPE_NOT_ACCESSIBLE, response.mealPlans.errors.single().reason)
    }

    @Test
    fun pullTombstonesARemovedMembersSharedPlanButNotAnActiveMembersPlan() = withService { service, repo ->
        val householdId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val removedMemberId = UUID.randomUUID()
        val activeMemberId = UUID.randomUUID()
        repo.seedActiveHousehold(ownerId, householdId)
        repo.seedActiveHousehold(activeMemberId, householdId)
        repo.seedRemovedFromHousehold(removedMemberId, householdId, serverRemovedAtMillis = 5000L)

        val planId = UUID.randomUUID()
        repo.seedMealPlan(
            buildMealPlan(planId, updatedAt = 1000L, ownerId = ownerId).copy(householdId = householdId.toString()),
            serverUpdatedAtMillis = 1000L
        )

        val removedPull = service.pullRecipes(removedMemberId, sinceMillis = 0L, limit = 10)
        val tombstoned = removedPull.mealPlans.firstOrNull { it.uuid == planId.toString() }
        assertNotNull(tombstoned)
        assertEquals(5000L, tombstoned.deletedAt)

        val activePull = service.pullRecipes(activeMemberId, sinceMillis = 0L, limit = 10)
        val stillShared = activePull.mealPlans.firstOrNull { it.uuid == planId.toString() }
        assertNotNull(stillShared)
        assertEquals(null, stillShared.deletedAt)
    }

    @Test
    fun pullIncludesAHouseholdMatesOldPreCursorPrivateRecipeReferencedByASharedPlan() = withService { service, repo ->
        val householdId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        repo.seedActiveHousehold(ownerId, householdId)
        repo.seedActiveHousehold(memberId, householdId)

        val ingredientId = repo.seedIngredient(serverUpdatedAt = 100L)
        val privateRecipe = sampleRecipe(UUID.randomUUID(), ownerId, updatedAt = 100L, ingredientId = ingredientId)
        repo.seedRecipe(privateRecipe, serverUpdatedAtMillis = 100L)

        val planId = UUID.randomUUID()
        repo.seedMealPlan(
            buildMealPlan(planId, updatedAt = 1000L, ownerId = ownerId).copy(
                householdId = householdId.toString(),
                days = listOf(
                    SyncMealPlanDayDto(
                        uuid = UUID.randomUUID().toString(),
                        dayIndex = 0,
                        dinnerRecipeId = privateRecipe.uuid,
                        lunchRecipeId = null
                    )
                )
            ),
            serverUpdatedAtMillis = 1000L
        )

        // Cursor is well past the recipe's own old server_updated_at (100ms) — a plain delta
        // query would never return it. Only the gap clause does.
        val response = service.pullRecipes(memberId, sinceMillis = 500L, limit = 10)

        assertTrue(response.recipes.any { it.uuid == privateRecipe.uuid })
        assertTrue(response.ingredients.any { it.uuid == ingredientId.toString() })
    }

    @Test
    fun pullExcludesAHouseholdMatesUnreferencedPrivateRecipe() = withService { service, repo ->
        val householdId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        repo.seedActiveHousehold(ownerId, householdId)
        repo.seedActiveHousehold(memberId, householdId)

        val ingredientId = repo.seedIngredient()
        val unrelatedPrivateRecipe = sampleRecipe(UUID.randomUUID(), ownerId, updatedAt = 100L, ingredientId = ingredientId)
        repo.seedRecipe(unrelatedPrivateRecipe, serverUpdatedAtMillis = 100L)
        // Deliberately not referenced by any meal plan day.

        val response = service.pullRecipes(memberId, sinceMillis = 500L, limit = 10)

        assertTrue(response.recipes.none { it.uuid == unrelatedPrivateRecipe.uuid })
    }

    @Test
    fun pullCreatorsIncludesMealPlanOwnersEvenWhenTheyAuthoredNoRecipesOnThePage() = withService { service, repo ->
        val householdId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        repo.seedActiveHousehold(ownerId, householdId)
        repo.seedActiveHousehold(memberId, householdId)
        repo.seedUser(ownerId)

        val planId = UUID.randomUUID()
        repo.seedMealPlan(
            buildMealPlan(planId, updatedAt = 1000L, ownerId = ownerId).copy(householdId = householdId.toString()),
            serverUpdatedAtMillis = 1000L
        )

        val response = service.pullRecipes(memberId, sinceMillis = 0L, limit = 10)

        assertTrue(response.creators.any { it.uuid == ownerId.toString() })
    }

    @Test
    fun getRecipeDetailReturnsAPrivateRecipeVisibleOnlyThroughHouseholdSharing() = withService { service, repo ->
        val householdId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        repo.seedActiveHousehold(ownerId, householdId)
        repo.seedActiveHousehold(memberId, householdId)

        val ingredientId = repo.seedIngredient()
        val privateRecipe = sampleRecipe(UUID.randomUUID(), ownerId, updatedAt = 100L, ingredientId = ingredientId)
        repo.seedRecipe(privateRecipe, serverUpdatedAtMillis = 100L)

        val planId = UUID.randomUUID()
        repo.seedMealPlan(
            buildMealPlan(planId, updatedAt = 1000L, ownerId = ownerId).copy(
                householdId = householdId.toString(),
                days = listOf(
                    SyncMealPlanDayDto(
                        uuid = UUID.randomUUID().toString(),
                        dayIndex = 0,
                        dinnerRecipeId = privateRecipe.uuid,
                        lunchRecipeId = null
                    )
                )
            ),
            serverUpdatedAtMillis = 1000L
        )

        val result = service.getRecipeDetail(memberId, UUID.fromString(privateRecipe.uuid))

        assertTrue(result is RecipeDetailResult.Found)
    }

    @Test
    fun getRecipeDetailStillHidesAPrivateRecipeNotSharedViaAnyPlan() = withService { service, repo ->
        val householdId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        repo.seedActiveHousehold(ownerId, householdId)
        repo.seedActiveHousehold(memberId, householdId)

        val ingredientId = repo.seedIngredient()
        val privateRecipe = sampleRecipe(UUID.randomUUID(), ownerId, updatedAt = 100L, ingredientId = ingredientId)
        repo.seedRecipe(privateRecipe, serverUpdatedAtMillis = 100L)
        // Not referenced by any plan — household sharing never applies to it.

        val result = service.getRecipeDetail(memberId, UUID.fromString(privateRecipe.uuid))

        assertEquals(RecipeDetailResult.NotFound, result)
    }

    // ── Regression: cross-tenant push (security audit F1/F3) ────────────────────
    //
    // A push names its own creatorId, so the pre-existing CREATOR_MISMATCH check only proves the
    // payload is self-consistent. These cover the second check — that the row already at that
    // uuid belongs to the caller.

    @Test
    fun pushRejectsRecipeOwnedByAnotherUserEvenWithFutureUpdatedAt() = withService { service, repo ->
        val victimId = UUID.randomUUID()
        val attackerId = UUID.randomUUID()
        val recipeId = UUID.randomUUID()
        val ingredientId = repo.seedIngredient()
        repo.seedRecipe(
            sampleRecipe(recipeId, victimId, updatedAt = 1000L, ingredientId = ingredientId)
                .copy(title = "Victim recipe"),
            serverUpdatedAtMillis = 2000L
        )

        // Long.MAX_VALUE defeats the staleness check, which is not a permission check.
        val takeover = sampleRecipe(recipeId, attackerId, updatedAt = Long.MAX_VALUE, ingredientId = ingredientId)
            .copy(title = "PWNED", privacy = "PUBLIC")

        val response = service.pushRecipes(attackerId, SyncPushRequest(listOf(takeover)))

        assertEquals(0, response.accepted.size, "takeover must not be accepted")
        assertEquals(0, response.conflicts.size)
        assertEquals(1, response.errors.size)
        assertEquals(SyncErrors.CREATOR_MISMATCH, response.errors.first().reason)

        val stored = repo.getRecipe(recipeId)
        assertEquals("Victim recipe", stored?.recipe?.title, "victim's recipe must be untouched")
        assertEquals(victimId.toString(), stored?.recipe?.creatorId, "ownership must not transfer")
    }

    @Test
    fun pushDoesNotLeakAnotherUsersRecipeInConflictPayload() = withService { service, repo ->
        val victimId = UUID.randomUUID()
        val attackerId = UUID.randomUUID()
        val recipeId = UUID.randomUUID()
        val ingredientId = repo.seedIngredient()
        repo.seedRecipe(
            sampleRecipe(recipeId, victimId, updatedAt = 9000L, ingredientId = ingredientId)
                .copy(title = "Victim secret recipe"),
            serverUpdatedAtMillis = 9000L
        )

        // updatedAt = 0 forces the server-is-newer branch, which used to echo serverVersion back.
        val probe = sampleRecipe(recipeId, attackerId, updatedAt = 0L, ingredientId = ingredientId)

        val response = service.pushRecipes(attackerId, SyncPushRequest(listOf(probe)))

        assertTrue(response.conflicts.isEmpty(), "must not answer with a conflict carrying the row")
        assertEquals(1, response.errors.size)
        assertEquals(SyncErrors.CREATOR_MISMATCH, response.errors.first().reason)
        assertFalse(
            response.toString().contains("Victim secret recipe"),
            "no part of the response may carry the victim's recipe"
        )
    }

    @Test
    fun pushStillAcceptsAnUpdateToTheCallersOwnRecipe() = withService { service, repo ->
        val userId = UUID.randomUUID()
        val recipeId = UUID.randomUUID()
        val ingredientId = repo.seedIngredient()
        repo.seedRecipe(
            sampleRecipe(recipeId, userId, updatedAt = 1000L, ingredientId = ingredientId),
            serverUpdatedAtMillis = 1000L
        )

        val update = sampleRecipe(recipeId, userId, updatedAt = 5000L, ingredientId = ingredientId)
            .copy(title = "Renamed by owner")

        val response = service.pushRecipes(userId, SyncPushRequest(listOf(update)))

        assertEquals(1, response.accepted.size)
        assertEquals(0, response.errors.size)
        assertEquals("Renamed by owner", repo.getRecipe(recipeId)?.recipe?.title)
    }

    // ── Regression: pull limit (security audit F5) ──────────────────────────────

    @Test
    fun pullClampsLimitInsteadOfOverflowing() = withService { service, repo ->
        val userId = UUID.randomUUID()
        val ingredientId = repo.seedIngredient()
        repeat(3) { i ->
            repo.seedRecipe(
                sampleRecipe(UUID.randomUUID(), userId, updatedAt = 1000L, ingredientId = ingredientId),
                serverUpdatedAtMillis = (i + 1) * 1000L
            )
        }

        // limit + 1 used to overflow to Int.MIN_VALUE and reach Exposed's .limit() as a negative.
        val response = service.pullRecipes(userId, sinceMillis = 0L, limit = Int.MAX_VALUE)

        assertEquals(3, response.recipes.size)
        assertFalse(response.hasMore)
    }

    @Test
    fun pullClampReportsHasMoreRatherThanReturningEverything() = withService { service, repo ->
        val userId = UUID.randomUUID()
        val ingredientId = repo.seedIngredient()
        repeat(SyncService.MAX_PULL_LIMIT + 10) { i ->
            repo.seedRecipe(
                sampleRecipe(UUID.randomUUID(), userId, updatedAt = 1000L, ingredientId = ingredientId),
                serverUpdatedAtMillis = (i + 1).toLong()
            )
        }

        val response = service.pullRecipes(userId, sinceMillis = 0L, limit = Int.MAX_VALUE)

        assertEquals(SyncService.MAX_PULL_LIMIT, response.recipes.size)
        assertTrue(response.hasMore, "clamped page must still advertise more")
    }

    private fun sampleRecipe(
        uuid: UUID,
        creatorId: UUID,
        updatedAt: Long,
        ingredientId: UUID,
        privacy: String = "PRIVATE"
    ): SyncRecipe = SyncRecipe(
        uuid = uuid.toString(),
        title = "Recipe",
        description = "Description",
        imageUrl = "https://example.com/image.jpg",
        imageUrlThumbnail = "https://example.com/thumb.jpg",
        prepTimeMinutes = 10,
        cookTimeMinutes = 20,
        servings = 2,
        creatorId = creatorId.toString(),
        recipeExternalUrl = null,
        privacy = privacy,
        updatedAt = updatedAt,
        deletedAt = null,
        steps = listOf(SyncRecipeStep(UUID.randomUUID().toString(), 0, "Step")),
        ingredients = listOf(SyncRecipeIngredient(ingredientId.toString(), 1.0, "unit")),
        tagIds = emptyList(),
        labelIds = emptyList()
    )

    private fun withService(block: suspend TestApplicationBuilder.(SyncService, FakeSyncRepository) -> Unit) {
        testApplication {
            val repo = FakeSyncRepository()
            val service = SyncService(repo, KtorSimpleLogger("SyncServiceTest"), FakeUserPreferencesRepository())
            block(service, repo)
        }
    }

    private fun buildMealPlan(
        planId: UUID,
        updatedAt: Long,
        ownerId: UUID = UUID.randomUUID(),
        preferencesJson: String = """{"planLengthDays":3,"mealType":"DINNER","recipeSource":"INCLUDE_PUBLIC"}"""
    ) = SyncMealPlanDto(
        uuid = planId.toString(),
        ownerId = ownerId.toString(),
        name = "Week Plan",
        status = "DRAFT",
        preferencesJson = preferencesJson,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        deletedAt = null,
        days = listOf(SyncMealPlanDayDto(uuid = UUID.randomUUID().toString(), dayIndex = 0, dinnerRecipeId = null, lunchRecipeId = null))
    )
}
