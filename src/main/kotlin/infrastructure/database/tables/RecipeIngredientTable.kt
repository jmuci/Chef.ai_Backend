package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object RecipeIngredientTable : Table("recipe_ingredients") {
    val recipeId = reference("recipeId", RecipeTable)
    val ingredientId = reference("ingredientId", IngredientTable)
    val quantity = double("quantity")
    val unit = text("unit")
    val updatedAt = long("updatedAt")
    val deletedAt = long("deletedAt").nullable()
    val syncState = text("syncState")
    val serverUpdatedAt = timestamp("serverUpdatedAt")
    override val primaryKey = PrimaryKey(recipeId, ingredientId)
}
