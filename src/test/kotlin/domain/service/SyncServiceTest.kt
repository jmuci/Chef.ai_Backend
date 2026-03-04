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
