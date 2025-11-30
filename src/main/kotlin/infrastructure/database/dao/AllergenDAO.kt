package com.tenmilelabs.infrastructure.database.dao

import com.tenmilelabs.infrastructure.database.tables.AllergenTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class AllergenDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AllergenDAO>(AllergenTable)

    var displayName by AllergenTable.displayName
    var updatedAt by AllergenTable.updatedAt
    var deletedAt by AllergenTable.deletedAt
    var syncState by AllergenTable.syncState
    var serverUpdatedAt by AllergenTable.serverUpdatedAt
}
