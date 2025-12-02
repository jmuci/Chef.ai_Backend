package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object RecipeStepTable : UUIDTable("recipe_steps", "uuid") {
    val recipe_id = reference("recipe_id", RecipeTable)
    val order_index = integer("order_index")
    val instruction = text("instruction")
    val updated_at = long("updated_at")
    val deleted_at = long("deleted_at").nullable()
    val sync_state = text("sync_state")
    val server_updated_at = timestamp("server_updated_at")
}
