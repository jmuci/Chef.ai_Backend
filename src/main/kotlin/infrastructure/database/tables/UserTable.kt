package com.tenmilelabs.infrastructure.database.tables

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp


object UserTable : UUIDTable("users", "uuid") {
    val user_name = text("user_name") // TODO email as username
    val display_name = text("display_name").default("")
    val email = text("email").uniqueIndex()
    val avatar_url = text("avatar_url").default("")
    val password_hash = varchar("password_hash", 255)
    val created_at = timestamp("created_at").clientDefault { Clock.System.now() }
    val updated_at = timestamp("updated_at").clientDefault { Clock.System.now() }
}
