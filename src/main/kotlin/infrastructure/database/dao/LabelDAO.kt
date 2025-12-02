package com.tenmilelabs.infrastructure.database.dao

import com.tenmilelabs.infrastructure.database.tables.LabelTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class LabelDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<LabelDAO>(LabelTable)

    var displayName by LabelTable.display_name
    var updatedAt by LabelTable.updated_at
    var deletedAt by LabelTable.deleted_at
    var syncState by LabelTable.sync_state
    var serverUpdatedAt by LabelTable.server_updated_at
}
