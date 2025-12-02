package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object RecipeTagTable : Table("recipe_tags") {
    val recipeId = reference("recipeId", RecipeTable)
    val tagId = reference("tagId", TagTable)
    val updatedAt = long("updatedAt")
    val deletedAt = long("deletedAt").nullable()
    val syncState = text("syncState")
    val serverUpdatedAt = timestamp("serverUpdatedAt")
    override val primaryKey = PrimaryKey(recipeId, tagId)
}
