package com.tenmilelabs.infrastructure.database.integration

import com.tenmilelabs.application.dto.SyncRecipe
import com.tenmilelabs.domain.model.ImageProvenance
import com.tenmilelabs.infrastructure.database.initDatabaseAndSchema
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresImageBlobRepository
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresSyncRepository
import com.tenmilelabs.infrastructure.database.tables.UserTable
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Covers the cross-repository behavior from
 * docs/recipe-image-architecture.md § Reclamation: a recipe soft-deleted through
 * `/sync/push` must dereference the blob it was pointing at, exactly like the repoint/clear
 * paths in [PostgresImageBlobRepository]. Both repositories reach into the same `recipes`
 * table, which the in-memory fakes used by the HTTP-level tests don't share — so this needs a
 * real Postgres to exercise.
 */
@Tag("db-integration")
class PostgresImageBlobRepositoryIntegrationTest {

    @BeforeEach
    fun resetSchema() {
        Database.connect(
            url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/chefai_db",
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
    fun softDeletingTheOnlyRecipePointingAtABlobMarksItUnreferenced() = runBlocking {
        val syncRepository = PostgresSyncRepository()
        val imageBlobRepository = PostgresImageBlobRepository()

        val ownerId = UUID.randomUUID()
        val recipeId = UUID.randomUUID()

        transaction {
            UserTable.insert {
                it[UserTable.id] = EntityID(ownerId, UserTable)
                it[user_name] = "owner"
                it[email] = "owner-$ownerId@example.com"
                it[display_name] = "Owner"
                it[avatar_url] = ""
                it[password_hash] = "hash"
            }
        }

        val recipe = SyncRecipe(
            uuid = recipeId.toString(),
            title = "Carbonara",
            description = "Classic",
            imageUrl = "", // blank => USER provenance
            imageUrlThumbnail = "",
            prepTimeMinutes = 10,
            cookTimeMinutes = 20,
            servings = 2,
            creatorId = ownerId.toString(),
            recipeExternalUrl = null,
            privacy = "PRIVATE",
            updatedAt = 1000L,
            deletedAt = null,
            steps = emptyList(),
            ingredients = emptyList(),
            tagIds = emptyList(),
            labelIds = emptyList()
        )
        val now = Clock.System.now()
        syncRepository.upsertRecipeAggregate(recipe, now)

        val hash = "a".repeat(64)
        imageBlobRepository.insertBlob(
            userId = ownerId,
            contentHash = hash,
            provenance = ImageProvenance.USER,
            mimeType = "image/jpeg",
            byteSize = 3L,
            storageKey = "$ownerId/$hash",
            createdAt = now.toEpochMilliseconds()
        )
        imageBlobRepository.setRecipeImageBlobId(recipeId, hash, now.toEpochMilliseconds())

        // Sanity: freshly pointed-at blob is referenced.
        assertNull(imageBlobRepository.findBlob(ownerId, hash)?.unreferencedSince)

        // Soft-delete the recipe through the same path /sync/push uses.
        val deleteInstant = Clock.System.now()
        syncRepository.upsertRecipeAggregate(recipe.copy(deletedAt = deleteInstant.toEpochMilliseconds()), deleteInstant)

        val blob = imageBlobRepository.findBlob(ownerId, hash)
        assertNotNull(blob)
        assertNotNull(blob.unreferencedSince)
        assertEquals(deleteInstant.toEpochMilliseconds(), blob.unreferencedSince)
    }

    @Test
    fun repointingARecipeToADifferentBlobDereferencesThePrevious() = runBlocking {
        val imageBlobRepository = PostgresImageBlobRepository()
        val ownerId = seedUser()
        val recipeId = seedRecipe(ownerId)

        val firstHash = "a".repeat(64)
        val secondHash = "b".repeat(64)
        val now = Clock.System.now().toEpochMilliseconds()
        imageBlobRepository.insertBlob(
            ownerId, firstHash, ImageProvenance.USER, "image/jpeg", 3L, "$ownerId/$firstHash", now
        )
        imageBlobRepository.insertBlob(
            ownerId, secondHash, ImageProvenance.USER, "image/jpeg", 3L, "$ownerId/$secondHash", now
        )
        imageBlobRepository.setRecipeImageBlobId(recipeId, firstHash, now)

        // Mirrors what RecipeImageService.uploadImage does on repoint: point at the new blob,
        // then dereference whatever the recipe pointed at before.
        imageBlobRepository.setRecipeImageBlobId(recipeId, secondHash, now)
        imageBlobRepository.refreshUnreferencedState(ownerId, firstHash, now)

        assertNotNull(imageBlobRepository.findBlob(ownerId, firstHash)?.unreferencedSince)
        assertNull(imageBlobRepository.findBlob(ownerId, secondHash)?.unreferencedSince)
    }

    @Test
    fun clearingARecipesPointerDereferencesTheBlob() = runBlocking {
        val imageBlobRepository = PostgresImageBlobRepository()
        val ownerId = seedUser()
        val recipeId = seedRecipe(ownerId)

        val hash = "c".repeat(64)
        val now = Clock.System.now().toEpochMilliseconds()
        imageBlobRepository.insertBlob(ownerId, hash, ImageProvenance.USER, "image/jpeg", 3L, "$ownerId/$hash", now)
        imageBlobRepository.setRecipeImageBlobId(recipeId, hash, now)

        imageBlobRepository.setRecipeImageBlobId(recipeId, null, now)
        imageBlobRepository.refreshUnreferencedState(ownerId, hash, now)

        assertNull(imageBlobRepository.findRecipeForImage(recipeId)?.imageBlobId)
        assertNotNull(imageBlobRepository.findBlob(ownerId, hash)?.unreferencedSince)
    }

    @Test
    fun sumByteSizeForUserOnlyCountsThatUsersBlobs() = runBlocking {
        val imageBlobRepository = PostgresImageBlobRepository()
        val ownerId = seedUser()
        val otherUserId = seedUser()
        val now = Clock.System.now().toEpochMilliseconds()

        imageBlobRepository.insertBlob(
            ownerId, "d".repeat(64), ImageProvenance.USER, "image/jpeg", 100L, "k1", now
        )
        imageBlobRepository.insertBlob(
            ownerId, "e".repeat(64), ImageProvenance.USER, "image/jpeg", 250L, "k2", now
        )
        imageBlobRepository.insertBlob(
            otherUserId, "f".repeat(64), ImageProvenance.USER, "image/jpeg", 999L, "k3", now
        )

        assertEquals(350L, imageBlobRepository.sumByteSizeForUser(ownerId))
    }

    @Test
    fun findExpiredUnreferencedBlobsReturnsOnlyThosePastTheCutoffOldestFirstAndLimited() = runBlocking {
        val imageBlobRepository = PostgresImageBlobRepository()
        val ownerId = seedUser()
        val now = Clock.System.now().toEpochMilliseconds()
        val cutoff = now

        val old = imageBlobRepository.insertBlob(
            ownerId, "1".repeat(64), ImageProvenance.USER, "image/jpeg", 1L, "old", now
        )
        val older = imageBlobRepository.insertBlob(
            ownerId, "2".repeat(64), ImageProvenance.USER, "image/jpeg", 1L, "older", now
        )
        val recent = imageBlobRepository.insertBlob(
            ownerId, "3".repeat(64), ImageProvenance.USER, "image/jpeg", 1L, "recent", now
        )
        imageBlobRepository.refreshUnreferencedState(ownerId, old.contentHash, cutoff - 1_000)
        imageBlobRepository.refreshUnreferencedState(ownerId, older.contentHash, cutoff - 2_000)
        imageBlobRepository.refreshUnreferencedState(ownerId, recent.contentHash, cutoff + 10_000)

        val expired = imageBlobRepository.findExpiredUnreferencedBlobs(cutoff, limit = 1)

        assertEquals(1, expired.size)
        assertEquals(older.contentHash, expired.single().contentHash) // oldest unreferenced_since first
    }

    private fun seedUser(): UUID {
        val userId = UUID.randomUUID()
        transaction {
            UserTable.insert {
                it[UserTable.id] = EntityID(userId, UserTable)
                it[user_name] = "user"
                it[email] = "user-$userId@example.com"
                it[display_name] = "User"
                it[avatar_url] = ""
                it[password_hash] = "hash"
            }
        }
        return userId
    }

    private suspend fun seedRecipe(ownerId: UUID): UUID {
        val recipeId = UUID.randomUUID()
        val recipe = SyncRecipe(
            uuid = recipeId.toString(),
            title = "Carbonara",
            description = "Classic",
            imageUrl = "",
            imageUrlThumbnail = "",
            prepTimeMinutes = 10,
            cookTimeMinutes = 20,
            servings = 2,
            creatorId = ownerId.toString(),
            recipeExternalUrl = null,
            privacy = "PRIVATE",
            updatedAt = 1000L,
            deletedAt = null,
            steps = emptyList(),
            ingredients = emptyList(),
            tagIds = emptyList(),
            labelIds = emptyList()
        )
        PostgresSyncRepository().upsertRecipeAggregate(recipe, Clock.System.now())
        return recipeId
    }
}
