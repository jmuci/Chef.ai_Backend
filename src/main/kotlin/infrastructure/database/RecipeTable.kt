package com.tenmilelabs.infrastructure.database

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object RecipeTable : UUIDTable("recipe", "uuid") {
    val title = varchar("title", 200)
    val label = varchar("label", 20) //TODO there should be a table for labels
    val description = text("description")
    val prepTimeMins = integer("prep_time_mins")
    val recipeUrl = text("recipe_url")
    val imageUrl = text("image_url")
    val imageUrlThumbnail = text("image_url_thumbnail")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val userId = uuid("user_id")
    val isPublic = bool("is_public").default(false)
}
