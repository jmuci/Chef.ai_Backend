package com.tenmilelabs.infrastructure.database

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object UserTable : UUIDTable("users", "id") {
    val email = varchar("email", 255).uniqueIndex()
    val username = varchar("username", 100)
    val passwordHash = varchar("password_hash", 255)
    val createdAt = timestamp("created_at").clientDefault { kotlinx.datetime.Clock.System.now() }
}
