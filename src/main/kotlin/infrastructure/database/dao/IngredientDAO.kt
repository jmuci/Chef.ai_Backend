package com.tenmilelabs.infrastructure.database.dao

import com.tenmilelabs.infrastructure.database.tables.IngredientTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class IngredientDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<IngredientDAO>(IngredientTable)

    var displayName by IngredientTable.display_name
    var allergenId by IngredientTable.allergen_id
    var sourcePrimaryId by IngredientTable.source_primary_id
    var updatedAt by IngredientTable.updated_at
    var deletedAt by IngredientTable.deleted_at
    var serverUpdatedAt by IngredientTable.server_updated_at
}
