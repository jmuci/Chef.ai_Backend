package com.tenmilelabs.infrastructure.database.dao

import com.tenmilelabs.infrastructure.database.tables.TagTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class TagDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<TagDAO>(TagTable)

    var displayName by TagTable.display_name
    var updatedAt by TagTable.updated_at
    var deletedAt by TagTable.deleted_at
    var serverUpdatedAt by TagTable.server_updated_at
}
