package com.tenmilelabs.infrastructure.database.dao

import com.tenmilelabs.infrastructure.database.tables.IngredientTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class IngredientDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<IngredientDAO>(IngredientTable)

    var displayName by IngredientTable.displayName
    var allergenId by IngredientTable.allergenId
    var sourcePrimaryId by IngredientTable.sourcePrimaryId
    var updatedAt by IngredientTable.updatedAt
    var deletedAt by IngredientTable.deletedAt
    var syncState by IngredientTable.syncState
    var serverUpdatedAt by IngredientTable.serverUpdatedAt
}
