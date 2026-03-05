package domain.service

import com.tenmilelabs.application.dto.SyncPushRequest
import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.application.dto.SyncRecipeIngredient
import com.tenmilelabs.application.dto.SyncRecipeStep
import com.tenmilelabs.application.dto.ConflictReasons
import com.tenmilelabs.application.dto.SyncErrors
import com.tenmilelabs.domain.service.SyncService
import io.ktor.server.testing.TestApplicationBuilder
import io.ktor.server.testing.testApplication
import io.ktor.util.logging.KtorSimpleLogger
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            val service = SyncService(repo, KtorSimpleLogger("SyncServiceTest"))
            block(service, repo)
        }
    }
}
