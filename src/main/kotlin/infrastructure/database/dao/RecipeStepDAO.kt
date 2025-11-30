package com.tenmilelabs.infrastructure.database.dao

import com.tenmilelabs.infrastructure.database.tables.RecipeStepTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class RecipeStepDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<RecipeStepDAO>(RecipeStepTable)

    var recipeId by RecipeStepTable.recipeId // TODO use referencedOn for normal references ?
    var orderIndex by RecipeStepTable.orderIndex
    var instruction by RecipeStepTable.instruction
    var updatedAt by RecipeStepTable.updatedAt
    var deletedAt by RecipeStepTable.deletedAt
    var syncState by RecipeStepTable.syncState
    var serverUpdatedAt by RecipeStepTable.serverUpdatedAt
}
