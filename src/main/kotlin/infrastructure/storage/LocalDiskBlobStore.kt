package com.tenmilelabs.infrastructure.storage

import com.tenmilelabs.domain.repository.BlobStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.streams.toList

/**
 * Writes blobs to the local filesystem under [root], one file per key. `key` is expected to be
 * `"<userId>/<contentHash>"` (see [RecipeImageService][com.tenmilelabs.domain.service.RecipeImageService]),
 * so each user gets their own subdirectory.
 *
 * This is the boring, always-available implementation of [BlobStore] — see
 * docs/prompts/recipe-image-upload-backend-prompt.md §2 for why an object-store client is
 * deliberately not added here.
 */
class LocalDiskBlobStore(private val root: Path) : BlobStore {
    // Directories are created lazily (in put(), below) rather than here, so constructing a
    // store — e.g. as an unused default parameter in a test that never touches images — has
    // no filesystem side effects.

    override suspend fun put(key: String, bytes: ByteArray, mimeType: String): Unit = withContext(Dispatchers.IO) {
        val path = resolve(key)
        Files.createDirectories(path.parent)
        Files.write(path, bytes)
    }

    override suspend fun openRead(key: String): InputStream? = withContext(Dispatchers.IO) {
        val path = resolve(key)
        if (path.exists() && path.isRegularFile()) path.inputStream() else null
    }

    override suspend fun delete(key: String): Unit = withContext(Dispatchers.IO) {
        resolve(key).deleteIfExists()
        Unit
    }

    override suspend fun listAllKeys(): List<String> = withContext(Dispatchers.IO) {
        if (!root.exists()) return@withContext emptyList()
        Files.walk(root).use { stream ->
            stream.toList()
                .filter { it.isRegularFile() }
                .map { it.relativeTo(root).toString().replace(java.io.File.separatorChar, '/') }
        }
    }

    /** Rejects a key that would escape [root] via `..` segments. */
    private fun resolve(key: String): Path {
        val resolved = root.resolve(key).normalize()
        require(resolved.startsWith(root)) { "Blob key escapes storage root: $key" }
        return resolved
    }
}
