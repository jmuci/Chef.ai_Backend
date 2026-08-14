package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.domain.model.ImageBlobRecord
import com.tenmilelabs.domain.model.ImageProvenance
import com.tenmilelabs.domain.model.RecipeImageContext
import com.tenmilelabs.domain.repository.ImageBlobRepository
import java.util.UUID

class FakeImageBlobRepository : ImageBlobRepository {
    private val recipes = mutableMapOf<UUID, RecipeImageContext>()
    private val blobs = mutableMapOf<UUID, ImageBlobRecord>()

    fun seedRecipe(
        recipeId: UUID = UUID.randomUUID(),
        ownerId: UUID,
        privacy: String = "PRIVATE",
        imageUrl: String = "",
        imageBlobId: String? = null,
        updatedAtMillis: Long = 0L
    ): UUID {
        recipes[recipeId] = RecipeImageContext(
            recipeId = recipeId,
            ownerId = ownerId,
            privacy = privacy,
            imageUrl = imageUrl,
            imageBlobId = imageBlobId,
            updatedAtMillis = updatedAtMillis
        )
        return recipeId
    }

    fun removeRecipe(recipeId: UUID) {
        recipes.remove(recipeId)
    }

    fun blobFor(userId: UUID, contentHash: String): ImageBlobRecord? =
        blobs.values.firstOrNull { it.userId == userId && it.contentHash == contentHash }

    override suspend fun findRecipeForImage(recipeId: UUID): RecipeImageContext? = recipes[recipeId]

    override suspend fun findBlob(userId: UUID, contentHash: String): ImageBlobRecord? =
        blobFor(userId, contentHash)

    override suspend fun insertBlob(
        userId: UUID,
        contentHash: String,
        provenance: ImageProvenance,
        mimeType: String,
        byteSize: Long,
        storageKey: String,
        createdAt: Long
    ): ImageBlobRecord {
        val record = ImageBlobRecord(
            id = UUID.randomUUID(),
            userId = userId,
            contentHash = contentHash,
            provenance = provenance,
            mimeType = mimeType,
            byteSize = byteSize,
            storageKey = storageKey,
            createdAt = createdAt,
            unreferencedSince = null
        )
        blobs[record.id] = record
        return record
    }

    override suspend fun sumByteSizeForUser(userId: UUID): Long =
        blobs.values.filter { it.userId == userId }.sumOf { it.byteSize }

    override suspend fun setRecipeImageBlobId(recipeId: UUID, imageBlobId: String?, updatedAtMillis: Long) {
        val existing = recipes[recipeId] ?: return
        recipes[recipeId] = existing.copy(imageBlobId = imageBlobId, updatedAtMillis = updatedAtMillis)
    }

    override suspend fun refreshUnreferencedState(userId: UUID, contentHash: String, nowMillis: Long) {
        val stillReferenced = recipes.values.any { it.ownerId == userId && it.imageBlobId == contentHash }
        val blob = blobFor(userId, contentHash) ?: return
        blobs[blob.id] = blob.copy(unreferencedSince = if (stillReferenced) null else nowMillis)
    }

    override suspend fun findExpiredUnreferencedBlobs(cutoffMillis: Long, limit: Int): List<ImageBlobRecord> =
        blobs.values
            .filter { it.unreferencedSince != null && it.unreferencedSince < cutoffMillis }
            .sortedBy { it.unreferencedSince }
            .take(limit)

    override suspend fun deleteBlobRow(id: UUID) {
        blobs.remove(id)
    }

    override suspend fun allStorageKeys(): Set<String> = blobs.values.map { it.storageKey }.toSet()
}
