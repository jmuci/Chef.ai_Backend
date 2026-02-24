package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object RecipeLabelTable : Table("recipe_labels") {
    val recipeId = reference("recipe_id", RecipeTable)
    val labelId = reference("label_id", LabelTable)
    val updatedAt = long("updated_at")
    val deletedAt = long("deleted_at").nullable()
    val serverUpdatedAt = timestamp("server_updated_at")
    override val primaryKey = PrimaryKey(recipeId, labelId)
}
