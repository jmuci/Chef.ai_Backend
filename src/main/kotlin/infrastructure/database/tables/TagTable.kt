package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object TagTable : UUIDTable("tags", "uuid") {
    val displayName = text("displayName")
    val updatedAt = long("updatedAt")
    val deletedAt = long("deletedAt").nullable()
    val syncState = text("syncState")
    val serverUpdatedAt = timestamp("serverUpdatedAt")
}
