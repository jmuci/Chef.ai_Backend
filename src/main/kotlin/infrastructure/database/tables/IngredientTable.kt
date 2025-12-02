package com.tenmilelabs.infrastructure.database.tables

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object IngredientTable : UUIDTable("ingredients", "uuid") {
    val display_name = text("display_name")
    val allergen_id = reference("allergen_id", AllergenTable).nullable()
    val source_primary_id = uuid("source_primary_id").nullable()
    val updated_at = long("updated_at")
    val deleted_at = long("deleted_at").nullable()
    val server_updated_at = timestamp("server_updated_at").clientDefault { Clock.System.now() }
}
