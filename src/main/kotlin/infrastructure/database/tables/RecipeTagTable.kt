package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object RecipeTagTable : Table("recipe_tags") {
    val recipeId = reference("recipe_id", RecipeTable)
    val tagId = reference("tag_id", TagTable)
    val updatedAt = long("updated_at")
    val deletedAt = long("deleted_at").nullable()
    val serverUpdatedAt = timestamp("server_updated_at")
    override val primaryKey = PrimaryKey(recipeId, tagId)
}
