package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object RecipeStepTable : UUIDTable("recipe_steps", "uuid") {
    val recipeId = reference("recipeId", RecipeTable)
    val orderIndex = integer("orderIndex")
    val instruction = text("instruction")
    val updatedAt = long("updatedAt")
    val deletedAt = long("deletedAt").nullable()
    val syncState = text("syncState")
    val serverUpdatedAt = timestamp("serverUpdatedAt")
}
