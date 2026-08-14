package com.tenmilelabs.domain.repository

import com.tenmilelabs.domain.model.ImageBlobRecord
import com.tenmilelabs.domain.model.ImageProvenance
import com.tenmilelabs.domain.model.RecipeImageContext
import java.util.UUID

interface ImageBlobRepository {
    /**
     * Loads the fields of [recipeId] needed to authorize and resolve its hero image.
     * Returns null if the recipe does not exist or is soft-deleted.
     */
    suspend fun findRecipeForImage(recipeId: UUID): RecipeImageContext?

    /**
     * Looks up a blob by its natural key. Blobs are scoped per-user — see
     * docs/recipe-image-architecture.md § Data Model for why cross-user
     * deduplication is deliberately not done.
     */
    suspend fun findBlob(userId: UUID, contentHash: String): ImageBlobRecord?

    /**
     * Inserts a new blob row, or returns the existing one if [userId]+[contentHash]
     * already has a row (the bytes were already written to [BlobStore] by an earlier
     * upload for this user).
     */
    suspend fun insertBlob(
        userId: UUID,
        contentHash: String,
        provenance: ImageProvenance,
        mimeType: String,
        byteSize: Long,
        storageKey: String,
        createdAt: Long
    ): ImageBlobRecord

    /** Sum of `byte_size` across all of [userId]'s blobs, for quota enforcement. */
    suspend fun sumByteSizeForUser(userId: UUID): Long

    /**
     * Points [recipeId] at [imageBlobId] (or clears it, when null) and bumps both
     * `updated_at` and the sync cursor `server_updated_at` to [updatedAtMillis].
     */
    suspend fun setRecipeImageBlobId(recipeId: UUID, imageBlobId: String?, updatedAtMillis: Long)

    /**
     * Re-derives `unreferenced_since` for the ([userId], [contentHash]) blob: null if any
     * live (non-soft-deleted) recipe owned by [userId] still points at it, otherwise
     * [nowMillis]. Call after any mutation that can drop a recipe's last pointer to a blob —
     * repoint, clear, or soft-delete. See §6.
     */
    suspend fun refreshUnreferencedState(userId: UUID, contentHash: String, nowMillis: Long)

    /** Blobs that have been unreferenced since before [cutoffMillis], oldest first. */
    suspend fun findExpiredUnreferencedBlobs(cutoffMillis: Long, limit: Int): List<ImageBlobRecord>

    suspend fun deleteBlobRow(id: UUID)

    /** Every `storage_key` currently recorded, used to find storage objects with no matching row. */
    suspend fun allStorageKeys(): Set<String>
}
