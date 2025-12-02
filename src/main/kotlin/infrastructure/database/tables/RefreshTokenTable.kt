package com.tenmilelabs.infrastructure.database.tables

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.CustomFunction
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.kotlin.datetime.KotlinInstantColumnType
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object RefreshTokenTable : UUIDTable("refresh_tokens", "uuid") {
    val userId = uuid("user_id").index()
    val tokenHash = varchar("token_hash", 255).uniqueIndex() // Unique index - each token hash should be unique
    val expiresAt = timestamp("expires_at").index() // Index for efficient cleanup of expired tokens
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val isRevoked = bool("is_revoked").default(false)
    val revokedAt = timestamp("revoked_at").nullable()
}
