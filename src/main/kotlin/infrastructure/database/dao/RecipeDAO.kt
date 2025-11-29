package com.tenmilelabs.infrastructure.database.dao

import com.tenmilelabs.infrastructure.database.tables.RecipeTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class RecipeDAO(uuid: EntityID<UUID>) : UUIDEntity(uuid) {
    companion object : UUIDEntityClass<RecipeDAO>(RecipeTable)

    var title by RecipeTable.title
    var label by RecipeTable.label
    var description by RecipeTable.description
    var prepTimeMins by RecipeTable.prepTimeMins
    var recipeUrl by RecipeTable.recipeUrl
    var imageUrl by RecipeTable.imageUrl
    var imageUrlThumbnail by RecipeTable.imageUrlThumbnail
    var createdAt by RecipeTable.createdAt
    var userId by RecipeTable.userId
    var isPublic by RecipeTable.isPublic
}
