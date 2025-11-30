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
    var imageUrl by RecipeTable.imageUrl
    var imageUrlThumbnail by RecipeTable.imageUrlThumbnail
    var prepTimeMinutes by RecipeTable.prepTimeMinutes
    var cookTimeMinutes by RecipeTable.cookTimeMinutes
    var servings by RecipeTable.servings
    var creatorId by RecipeTable.creatorId
    var recipeExternalUrl by RecipeTable.recipeExternalUrl
    var privacy by RecipeTable.privacy
    var updatedAt by RecipeTable.updatedAt
    var deletedAt by RecipeTable.deletedAt
    var syncState by RecipeTable.syncState
    var serverUpdatedAt by RecipeTable.serverUpdatedAt
}
