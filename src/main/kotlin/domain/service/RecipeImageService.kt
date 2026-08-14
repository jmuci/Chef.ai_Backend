package com.tenmilelabs.domain.service

import com.tenmilelabs.domain.model.ImageProvenance
import com.tenmilelabs.domain.repository.BlobStore
import com.tenmilelabs.domain.repository.ImageBlobRepository
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

private val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")

sealed interface ImageUploadResult {
    data class Success(val imageBlobId: String, val updatedAtMillis: Long) : ImageUploadResult
    data object RecipeNotFound : ImageUploadResult
    data object UnsupportedMediaType : ImageUploadResult
    data object PayloadTooLarge : ImageUploadResult
    data object HashMismatch : ImageUploadResult
    data object ScrapedUploadDisabled : ImageUploadResult
    data object QuotaExceeded : ImageUploadResult
}

sealed interface ImageServeResult {
    data class Found(val stream: InputStream, val mimeType: String, val byteSize: Long, val contentHash: String) :
        ImageServeResult
    data class NotModified(val contentHash: String) : ImageServeResult
    data object NotFound : ImageServeResult
}

sealed interface ImageClearResult {
    data class Success(val updatedAtMillis: Long) : ImageClearResult
    data object NotFound : ImageClearResult
}

/**
 * Upload, serving, and clearing of a recipe's hero image blob.
 *
 * See docs/recipe-image-architecture.md for the endpoint contract this
 * implements and the reasoning behind each check.
 */
class RecipeImageService(
    private val imageBlobRepository: ImageBlobRepository,
    private val blobStore: BlobStore,
    private val config: ImageBlobConfig,
    private val log: Logger,
    private val clock: Clock = Clock.System
) {
    /**
     * [bytes] must already be capped at [ImageBlobConfig.maxUploadBytes] by the caller —
     * streaming the request body with an early abort past that cap is a presentation-layer
     * concern (it needs the raw `ByteReadChannel`), not something this pure-Kotlin service
     * can do.
     */
    suspend fun uploadImage(
        userId: UUID,
        recipeId: UUID,
        contentType: String?,
        declaredHashHex: String?,
        bytes: ByteArray
    ): ImageUploadResult {
        val recipe = imageBlobRepository.findRecipeForImage(recipeId)
        // Same response for "doesn't exist" and "not yours" — this endpoint must not be
        // usable as a recipe-existence oracle.
        if (recipe == null || recipe.ownerId != userId) {
            return ImageUploadResult.RecipeNotFound
        }

        val normalizedType = contentType?.substringBefore(';')?.trim()?.lowercase()
        if (normalizedType == null || normalizedType !in SUPPORTED_MIME_TYPES) {
            return ImageUploadResult.UnsupportedMediaType
        }

        if (bytes.size > config.maxUploadBytes) {
            return ImageUploadResult.PayloadTooLarge
        }

        val computedHash = sha256Hex(bytes)
        if (declaredHashHex == null || !computedHash.equals(declaredHashHex.trim(), ignoreCase = true)) {
            return ImageUploadResult.HashMismatch
        }

        // Never trust Content-Type alone — without this an authenticated caller could host
        // arbitrary files behind an image/* label.
        if (!matchesMagicBytes(bytes, normalizedType)) {
            return ImageUploadResult.UnsupportedMediaType
        }

        // Derived server-side, never accepted from the client: a blank imageUrl means this
        // device holds the only copy of the photo in existence.
        val provenance = if (recipe.imageUrl.isBlank()) ImageProvenance.USER else ImageProvenance.SCRAPED
        if (provenance == ImageProvenance.SCRAPED && !config.allowScrapedImageUpload) {
            return ImageUploadResult.ScrapedUploadDisabled
        }

        val currentTotal = imageBlobRepository.sumByteSizeForUser(userId)
        if (currentTotal + bytes.size > config.perUserQuotaBytes) {
            return ImageUploadResult.QuotaExceeded
        }

        val existingBlob = imageBlobRepository.findBlob(userId, computedHash)
        if (existingBlob != null && recipe.imageBlobId == computedHash) {
            // Re-uploading the bytes already backing this recipe is a no-op: touching
            // updated_at here would make the recipe reappear in every subsequent pull delta.
            log.info("Image upload no-op: recipeId=$recipeId userId=$userId hash=$computedHash")
            return ImageUploadResult.Success(computedHash, recipe.updatedAtMillis)
        }

        val now = clock.now().toEpochMilliseconds()
        val storageKey = "$userId/$computedHash"
        if (existingBlob == null) {
            blobStore.put(storageKey, bytes, normalizedType)
            imageBlobRepository.insertBlob(
                userId = userId,
                contentHash = computedHash,
                provenance = provenance,
                mimeType = normalizedType,
                byteSize = bytes.size.toLong(),
                storageKey = storageKey,
                createdAt = now
            )
        }

        val previousHash = recipe.imageBlobId
        imageBlobRepository.setRecipeImageBlobId(recipeId, computedHash, now)
        if (previousHash != null && previousHash != computedHash) {
            imageBlobRepository.refreshUnreferencedState(userId, previousHash, now)
        }

        log.info(
            "Image upload accepted: recipeId=$recipeId userId=$userId hash=$computedHash provenance=$provenance"
        )
        return ImageUploadResult.Success(computedHash, now)
    }

    suspend fun serveImage(callerUserId: UUID, recipeId: UUID, ifNoneMatch: String?): ImageServeResult {
        val recipe = imageBlobRepository.findRecipeForImage(recipeId) ?: return ImageServeResult.NotFound
        val hash = recipe.imageBlobId ?: return ImageServeResult.NotFound

        val isOwner = recipe.ownerId == callerUserId
        // Visibility follows the recipe, not the image's provenance.
        if (!isOwner && recipe.privacy != "PUBLIC") return ImageServeResult.NotFound

        val blob = imageBlobRepository.findBlob(recipe.ownerId, hash) ?: return ImageServeResult.NotFound

        if (!isOwner && blob.provenance == ImageProvenance.SCRAPED && !config.serveScrapedBlobsToNonOwners) {
            return ImageServeResult.NotFound
        }

        val normalizedIfNoneMatch = ifNoneMatch?.trim()?.trim('"')
        if (normalizedIfNoneMatch == hash) {
            return ImageServeResult.NotModified(hash)
        }

        // Defensive: the DB row can outlive the storage object (e.g. a crash between them).
        val stream = blobStore.openRead(blob.storageKey) ?: return ImageServeResult.NotFound
        return ImageServeResult.Found(stream, blob.mimeType, blob.byteSize, hash)
    }

    suspend fun clearImage(userId: UUID, recipeId: UUID): ImageClearResult {
        val recipe = imageBlobRepository.findRecipeForImage(recipeId) ?: return ImageClearResult.NotFound
        if (recipe.ownerId != userId) return ImageClearResult.NotFound

        val previousHash = recipe.imageBlobId
        val now = clock.now().toEpochMilliseconds()
        imageBlobRepository.setRecipeImageBlobId(recipeId, null, now)
        if (previousHash != null) {
            imageBlobRepository.refreshUnreferencedState(userId, previousHash, now)
        }
        log.info("Image cleared: recipeId=$recipeId userId=$userId previousHash=$previousHash")
        return ImageClearResult.Success(now)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun matchesMagicBytes(bytes: ByteArray, mimeType: String): Boolean = when (mimeType) {
        "image/jpeg" -> bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        "image/png" -> bytes.size >= 4 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        "image/webp" -> bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        else -> false
    }
}
