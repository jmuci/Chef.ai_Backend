package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object RecipeLabelTable : Table("recipe_labels") {
    val recipeId = reference("recipeId", RecipeTable)
    val labelId = reference("labelId", LabelTable)
    val updatedAt = long("updatedAt")
    val deletedAt = long("deletedAt").nullable()
    val syncState = text("syncState")
    val serverUpdatedAt = timestamp("serverUpdatedAt")
    override val primaryKey = PrimaryKey(recipeId, labelId)
}
