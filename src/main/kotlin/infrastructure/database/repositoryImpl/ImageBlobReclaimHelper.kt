package com.tenmilelabs.infrastructure.database.repositoryImpl

import com.tenmilelabs.infrastructure.database.tables.ImageBlobTable
import com.tenmilelabs.infrastructure.database.tables.RecipeTable
import com.tenmilelabs.infrastructure.database.tables.UserTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

/**
 * Re-derives `image_blobs.unreferenced_since` for one (user, hash) pair: null if any live
 * (non-soft-deleted) recipe owned by [userId] still points at [contentHash], otherwise
 * [nowMillis]. Must run inside an already-open transaction (call from within a
 * `suspendTransaction { }` block).
 *
 * Shared by [PostgresImageBlobRepository] (repoint via upload, clear via delete) and
 * [PostgresSyncRepository] (soft-delete via `/sync/push`) — the same dereference check applies
 * to every mutation that can drop a recipe's last pointer to a blob. See
 * docs/recipe-image-architecture.md § Reclamation.
 */
internal fun applyImageDereferenceCheck(userId: UUID, contentHash: String, nowMillis: Long) {
    val stillReferenced = RecipeTable
        .selectAll()
        .where {
            (RecipeTable.image_blob_id eq contentHash) and
                (RecipeTable.creator_id eq EntityID(userId, UserTable)) and
                RecipeTable.deleted_at.isNull()
        }
        .limit(1)
        .any()

    ImageBlobTable.update({
        (ImageBlobTable.user_id eq EntityID(userId, UserTable)) and (ImageBlobTable.content_hash eq contentHash)
    }) {
        it[unreferenced_since] = if (stillReferenced) null else nowMillis
    }
}
