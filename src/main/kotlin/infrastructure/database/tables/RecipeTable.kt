package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object RecipeTable : UUIDTable("recipes", "uuid") {
    val title = text("title")
    val description = text("description")
    val image_url = text("image_url")
    val image_url_thumbnail = text("image_url_thumbnail")
    val prep_time_minutes = integer("prep_time_minutes")
    val cook_time_minutes = integer("cook_time_minutes")
    val servings = integer("servings")
    val creator_id = uuid("creator_id")
    val recipe_external_url = text("recipe_external_url").nullable()
    val privacy = text("privacy")
    val updated_at = long("updated_at")
    val deleted_at = long("deleted_at").nullable()
    val server_updated_at = timestamp("server_updated_at")
}
