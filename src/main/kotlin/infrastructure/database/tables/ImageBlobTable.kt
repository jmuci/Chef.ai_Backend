package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

/**
 * Recipe hero image bytes, scoped per-user (not deduplicated across users — see
 * ADR-011 Stage 2 / docs/recipe-image-architecture.md.
 */
object ImageBlobTable : UUIDTable("image_blobs", "id") {
    val user_id = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)

    /** Lowercase sha256 hex of the stored bytes. */
    val content_hash = text("content_hash")

    /** USER | SCRAPED — derived server-side at upload time, never accepted from the client. */
    val provenance = text("provenance")
    val mime_type = text("mime_type")
    val byte_size = long("byte_size")

    /** Opaque outside [com.tenmilelabs.domain.repository.BlobStore]. */
    val storage_key = text("storage_key")
    val created_at = long("created_at")

    /** Set when no live recipe points at this blob; cleared back to null if referenced again. */
    val unreferenced_since = long("unreferenced_since").nullable().index()

    init {
        uniqueIndex(user_id, content_hash)
    }
}
