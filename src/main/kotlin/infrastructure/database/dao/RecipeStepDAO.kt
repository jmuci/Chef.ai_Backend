package com.tenmilelabs.infrastructure.database.dao

import com.tenmilelabs.infrastructure.database.tables.RecipeStepTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class RecipeStepDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<RecipeStepDAO>(RecipeStepTable)

    var recipeId by RecipeStepTable.recipe_id
    var orderIndex by RecipeStepTable.order_index
    var instruction by RecipeStepTable.instruction
    var updatedAt by RecipeStepTable.updated_at
    var deletedAt by RecipeStepTable.deleted_at
    var syncState by RecipeStepTable.sync_state
    var serverUpdatedAt by RecipeStepTable.server_updated_at
}
