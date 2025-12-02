package com.tenmilelabs.infrastructure.database.tables

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object UserTable : UUIDTable("users", "uuid") {
    val display_name = text("display_name")
    val email = text("email").uniqueIndex()
    val avatar_url = text("avatar_url")
    val password_hash = varchar("password_hash", 255)
    val created_at = timestamp("created_at").clientDefault { Clock.System.now() }
    val updated_at = timestamp("updated_at").clientDefault { Clock.System.now() }
    val deleted_at = long("deleted_at").nullable()
    val sync_state = text("sync_state")
    val server_updated_at = timestamp("server_updated_at")
}
