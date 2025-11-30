package com.tenmilelabs.infrastructure.database.dao

import com.tenmilelabs.infrastructure.database.tables.LabelTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class LabelDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<LabelDAO>(LabelTable)

    var displayName by LabelTable.displayName
    var updatedAt by LabelTable.updatedAt
    var deletedAt by LabelTable.deletedAt
    var syncState by LabelTable.syncState
    var serverUpdatedAt by LabelTable.serverUpdatedAt
}
