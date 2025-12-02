package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object SourceClassificationTable : UUIDTable("source_classifications", "uuid") {
    val category = text("category")
    val subcategory = text("subcategory").nullable()
    val updated_at = long("updated_at")
    val deleted_at = long("deleted_at").nullable()
    val server_updated_at = timestamp("server_updated_at")
}
