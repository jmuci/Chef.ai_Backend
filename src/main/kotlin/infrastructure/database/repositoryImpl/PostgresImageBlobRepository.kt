package com.tenmilelabs.infrastructure.database.repositoryImpl

import com.tenmilelabs.domain.model.ImageBlobRecord
import com.tenmilelabs.domain.model.ImageProvenance
import com.tenmilelabs.domain.model.RecipeImageContext
import com.tenmilelabs.domain.repository.ImageBlobRepository
import com.tenmilelabs.infrastructure.database.mappers.suspendTransaction
import com.tenmilelabs.infrastructure.database.tables.ImageBlobTable
import com.tenmilelabs.infrastructure.database.tables.RecipeTable
import com.tenmilelabs.infrastructure.database.tables.UserTable
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.update
import java.util.UUID

class PostgresImageBlobRepository : ImageBlobRepository {
    override suspend fun findRecipeForImage(recipeId: UUID): RecipeImageContext? = suspendTransaction {
        RecipeTable
            .selectAll()
            .where { (RecipeTable.id eq recipeId) and RecipeTable.deleted_at.isNull() }
            .firstOrNull()
            ?.let { row ->
                RecipeImageContext(
                    recipeId = row[RecipeTable.id].value,
                    ownerId = row[RecipeTable.creator_id].value,
                    privacy = row[RecipeTable.privacy],
                    imageUrl = row[RecipeTable.image_url],
                    imageBlobId = row[RecipeTable.image_blob_id],
                    updatedAtMillis = row[RecipeTable.updated_at]
                )
            }
    }

    override suspend fun findBlob(userId: UUID, contentHash: String): ImageBlobRecord? = suspendTransaction {
        ImageBlobTable
            .selectAll()
            .where {
                (ImageBlobTable.user_id eq EntityID(userId, UserTable)) and
                    (ImageBlobTable.content_hash eq contentHash)
            }
            .firstOrNull()
            ?.let(::toImageBlobRecord)
    }

    override suspend fun insertBlob(
        userId: UUID,
        contentHash: String,
        provenance: ImageProvenance,
        mimeType: String,
        byteSize: Long,
        storageKey: String,
        createdAt: Long
    ): ImageBlobRecord = suspendTransaction {
        val id = UUID.randomUUID()
        ImageBlobTable.insert {
            it[ImageBlobTable.id] = id
            it[user_id] = EntityID(userId, UserTable)
            it[content_hash] = contentHash
            it[ImageBlobTable.provenance] = provenance.name
            it[mime_type] = mimeType
            it[byte_size] = byteSize
            it[storage_key] = storageKey
            it[created_at] = createdAt
            it[unreferenced_since] = null
        }
        ImageBlobRecord(
            id = id,
            userId = userId,
            contentHash = contentHash,
            provenance = provenance,
            mimeType = mimeType,
            byteSize = byteSize,
            storageKey = storageKey,
            createdAt = createdAt,
            unreferencedSince = null
        )
    }

    override suspend fun sumByteSizeForUser(userId: UUID): Long = suspendTransaction {
        val sumColumn = ImageBlobTable.byte_size.sum()
        ImageBlobTable
            .select(sumColumn)
            .where { ImageBlobTable.user_id eq EntityID(userId, UserTable) }
            .firstOrNull()
            ?.get(sumColumn) ?: 0L
    }

    override suspend fun setRecipeImageBlobId(recipeId: UUID, imageBlobId: String?, updatedAtMillis: Long): Unit =
        suspendTransaction {
            RecipeTable.update({ RecipeTable.id eq recipeId }) {
                it[RecipeTable.image_blob_id] = imageBlobId
                it[RecipeTable.updated_at] = updatedAtMillis
                it[RecipeTable.server_updated_at] = Instant.fromEpochMilliseconds(updatedAtMillis)
            }
        }

    override suspend fun refreshUnreferencedState(userId: UUID, contentHash: String, nowMillis: Long): Unit =
        suspendTransaction {
            applyImageDereferenceCheck(userId, contentHash, nowMillis)
        }

    override suspend fun findExpiredUnreferencedBlobs(cutoffMillis: Long, limit: Int): List<ImageBlobRecord> =
        suspendTransaction {
            ImageBlobTable
                .selectAll()
                .where {
                    ImageBlobTable.unreferenced_since.isNotNull() and
                        (ImageBlobTable.unreferenced_since less cutoffMillis)
                }
                .orderBy(ImageBlobTable.unreferenced_since to SortOrder.ASC)
                .limit(limit)
                .map(::toImageBlobRecord)
        }

    override suspend fun deleteBlobRow(id: UUID): Unit = suspendTransaction {
        ImageBlobTable.deleteWhere { ImageBlobTable.id eq id }
        Unit
    }

    override suspend fun allStorageKeys(): Set<String> = suspendTransaction {
        ImageBlobTable.selectAll().map { it[ImageBlobTable.storage_key] }.toSet()
    }

    private fun toImageBlobRecord(row: ResultRow): ImageBlobRecord = ImageBlobRecord(
        id = row[ImageBlobTable.id].value,
        userId = row[ImageBlobTable.user_id].value,
        contentHash = row[ImageBlobTable.content_hash],
        provenance = ImageProvenance.valueOf(row[ImageBlobTable.provenance]),
        mimeType = row[ImageBlobTable.mime_type],
        byteSize = row[ImageBlobTable.byte_size],
        storageKey = row[ImageBlobTable.storage_key],
        createdAt = row[ImageBlobTable.created_at],
        unreferencedSince = row[ImageBlobTable.unreferenced_since]
    )
}
