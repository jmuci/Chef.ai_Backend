package com.tenmilelabs.infrastructure.database.dao

import com.tenmilelabs.infrastructure.database.tables.RecipeTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class RecipeDAO(uuid: EntityID<UUID>) : UUIDEntity(uuid) {
    companion object : UUIDEntityClass<RecipeDAO>(RecipeTable)

    var title by RecipeTable.title
    var description by RecipeTable.description
    var imageUrl by RecipeTable.image_url
    var imageUrlThumbnail by RecipeTable.image_url_thumbnail
    var prepTimeMinutes by RecipeTable.prep_time_minutes
    var cookTimeMinutes by RecipeTable.cook_time_minutes
    var servings by RecipeTable.servings
    var privacy by RecipeTable.privacy
    var creatorId by RecipeTable.creator_id
    var recipeExternalUrl by RecipeTable.recipe_external_url
    var updatedAt by RecipeTable.updated_at
    var deletedAt by RecipeTable.deleted_at
    var serverUpdatedAt by RecipeTable.server_updated_at
}
