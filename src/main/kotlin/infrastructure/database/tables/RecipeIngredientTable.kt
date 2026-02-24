package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object RecipeIngredientTable : Table("recipe_ingredients") {
    val recipeId = reference("recipe_id", RecipeTable)
    val ingredientId = reference("ingredient_id", IngredientTable)
    val quantity = double("quantity")
    val unit = text("unit")
    val updatedAt = long("updated_at")
    val deletedAt = long("deleted_at").nullable()
    val serverUpdatedAt = timestamp("server_updated_at")
    override val primaryKey = PrimaryKey(recipeId, ingredientId)
}
