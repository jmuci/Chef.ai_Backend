package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object BookmarkedRecipeTable : Table("bookmarked_recipes") {
    val user_id = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val recipe_id = reference("recipe_id", RecipeTable, onDelete = ReferenceOption.CASCADE)
    /** Server-assigned timestamp; used as the sync cursor for pull queries. */
    val updated_at = timestamp("updated_at").index()
    val deleted_at = timestamp("deleted_at").nullable()
    val sync_state = text("sync_state").default("PENDING")
    override val primaryKey = PrimaryKey(user_id, recipe_id)
}
