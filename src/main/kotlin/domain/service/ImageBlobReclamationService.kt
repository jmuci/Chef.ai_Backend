package com.tenmilelabs.domain.service

import com.tenmilelabs.domain.repository.BlobStore
import com.tenmilelabs.domain.repository.ImageBlobRepository
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock

/**
 * Hard-deletes blobs that have been unreferenced for longer than
 * [ImageBlobConfig.unreferencedRetentionDays], and separately sweeps storage objects that have
 * no matching [image_blobs][com.tenmilelabs.infrastructure.database.tables.ImageBlobTable] row
 * (crash-orphans from an upload that wrote bytes but died before the row committed).
 *
 * See docs/prompts/recipe-image-upload-backend-prompt.md §6. The retention window exists
 * because recipe delete is a soft delete with no undo — it is the only thing that would make a
 * future "undo delete" feature possible at all.
 */
data class ImageBlobReclamationConfig(
    val enabled: Boolean = false,
    val intervalHours: Long = 24,
    val batchSize: Int = 100
)

class ImageBlobReclamationService(
    private val imageBlobRepository: ImageBlobRepository,
    private val blobStore: BlobStore,
    private val imageBlobConfig: ImageBlobConfig,
    private val log: Logger,
    private val clock: Clock = Clock.System
) {
    suspend fun sweepOnce(batchSize: Int = DEFAULT_BATCH_SIZE): Int {
        if (imageBlobConfig.unreferencedRetentionDays <= 0 || batchSize <= 0) {
            log.warn("Skipping image blob reclamation due to invalid config: $imageBlobConfig")
            return 0
        }

        val cutoffMillis = clock.now().toEpochMilliseconds() -
            (imageBlobConfig.unreferencedRetentionDays * MILLIS_PER_DAY)
        val expired = imageBlobRepository.findExpiredUnreferencedBlobs(cutoffMillis, batchSize)

        var deleted = 0
        for (blob in expired) {
            // Storage object first, row second: an orphaned row is trivially recoverable by
            // this same sweep on its next pass, but an orphaned object is only findable by a
            // full scan.
            blobStore.delete(blob.storageKey)
            imageBlobRepository.deleteBlobRow(blob.id)
            deleted++
        }
        if (deleted > 0) {
            log.info("Image blob reclamation deleted $deleted expired blob(s), cutoffMillis=$cutoffMillis")
        }

        sweepOrphanStorageObjects()
        return deleted
    }

    private suspend fun sweepOrphanStorageObjects() {
        val knownKeys = imageBlobRepository.allStorageKeys()
        val orphanKeys = blobStore.listAllKeys().filterNot { it in knownKeys }
        orphanKeys.forEach { blobStore.delete(it) }
        if (orphanKeys.isNotEmpty()) {
            log.info("Image blob reclamation deleted ${orphanKeys.size} orphaned storage object(s)")
        }
    }

    companion object {
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        private const val DEFAULT_BATCH_SIZE = 100
    }
}
