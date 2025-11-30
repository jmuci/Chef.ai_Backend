package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object IngredientTable : UUIDTable("ingredients", "uuid") {
    val displayName = text("displayName")
    val allergenId = reference("allergenId", AllergenTable).nullable()
    val sourcePrimaryId = uuid("sourcePrimaryId").nullable()
    val updatedAt = long("updatedAt")
    val deletedAt = long("deletedAt").nullable()
    val syncState = text("syncState")
    val serverUpdatedAt = timestamp("serverUpdatedAt")
}
