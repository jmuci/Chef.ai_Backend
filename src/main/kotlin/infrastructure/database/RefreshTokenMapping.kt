package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.domain.model.RefreshToken
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.*

object RefreshTokenTable : UUIDTable("refresh_tokens", "id") {
    val userId = varchar("user_id", 255).index()
    val tokenHash = varchar("token_hash", 255)
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at").clientDefault { kotlinx.datetime.Clock.System.now() }
    val isRevoked = bool("is_revoked").default(false)
    val revokedAt = timestamp("revoked_at").nullable()
}

class RefreshTokenDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<RefreshTokenDAO>(RefreshTokenTable)

    var userId by RefreshTokenTable.userId
    var tokenHash by RefreshTokenTable.tokenHash
    var expiresAt by RefreshTokenTable.expiresAt
    var createdAt by RefreshTokenTable.createdAt
    var isRevoked by RefreshTokenTable.isRevoked
    var revokedAt by RefreshTokenTable.revokedAt
}

fun daoToRefreshToken(dao: RefreshTokenDAO): RefreshToken = RefreshToken(
    id = dao.id.toString(),
    userId = dao.userId,
    tokenHash = dao.tokenHash,
    expiresAt = dao.expiresAt.toString(),
    createdAt = dao.createdAt.toString(),
    isRevoked = dao.isRevoked,
    revokedAt = dao.revokedAt?.toString()
)
