package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object AllergenTable : UUIDTable("allergens", "uuid") {
    val display_name = text("display_name")
    val updated_at = long("updated_at")
    val deleted_at = long("deleted_at").nullable()
    val sync_state = text("sync_state")
    val server_updated_at = timestamp("server_updated_at")
}
