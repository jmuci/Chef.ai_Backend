package com.tenmilelabs.infrastructure.database.tables

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object UserTable : UUIDTable("users", "uuid") {
    val displayName = text("displayName")
    val email = text("email").uniqueIndex()
    val avatarUrl = text("avatarUrl")
    val passwordHash = varchar("password_hash", 255)
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val updatedAt = long("updatedAt")
    val deletedAt = long("deletedAt").nullable()
    val syncState = text("syncState")
    val serverUpdatedAt = timestamp("serverUpdatedAt")
}
