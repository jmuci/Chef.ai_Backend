package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object RecipeTable : UUIDTable("recipes", "uuid") {
    val title = text("title")
    val description = text("description")
    val imageUrl = text("imageUrl")
    val imageUrlThumbnail = text("imageUrlThumbnail")
    val prepTimeMinutes = integer("prepTimeMinutes")
    val cookTimeMinutes = integer("cookTimeMinutes")
    val servings = integer("servings")
    val creatorId = uuid("creatorId")
    val recipeExternalUrl = text("recipeExternalUrl").nullable()
    val privacy = text("privacy")
    val updatedAt = long("updatedAt")
    val deletedAt = long("deletedAt").nullable()
    val syncState = text("syncState")
    val serverUpdatedAt = timestamp("serverUpdatedAt")
}
