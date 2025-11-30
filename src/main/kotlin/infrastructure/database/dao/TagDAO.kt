package com.tenmilelabs.infrastructure.database.dao

import com.tenmilelabs.infrastructure.database.tables.TagTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class TagDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<TagDAO>(TagTable)

    var displayName by TagTable.displayName
    var updatedAt by TagTable.updatedAt
    var deletedAt by TagTable.deletedAt
    var syncState by TagTable.syncState
    var serverUpdatedAt by TagTable.serverUpdatedAt
}
