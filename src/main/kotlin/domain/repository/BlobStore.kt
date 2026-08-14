package com.tenmilelabs.domain.repository

import java.io.InputStream

/**
 * Seam between the image-blob endpoints and wherever bytes actually live. `key` is opaque
 * outside implementations — callers must not assume any structure to it.
 *
 * [LocalDiskBlobStore][com.tenmilelabs.infrastructure.storage.LocalDiskBlobStore] is the only
 * implementation for now. Swapping in an object-store client later touches this interface's
 * implementation only — no endpoint or client-facing change. See
 * docs/prompts/recipe-image-upload-backend-prompt.md §2.
 */
interface BlobStore {
    suspend fun put(key: String, bytes: ByteArray, mimeType: String)

    /** Returns null if no object exists at [key]. */
    suspend fun openRead(key: String): InputStream?

    suspend fun delete(key: String)

    /** Every key currently in the store. Used by the reclamation sweep to find crash-orphans. */
    suspend fun listAllKeys(): List<String>
}
