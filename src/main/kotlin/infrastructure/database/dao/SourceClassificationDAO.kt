package com.tenmilelabs.infrastructure.database.dao

import com.tenmilelabs.infrastructure.database.tables.SourceClassificationTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class SourceClassificationDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<SourceClassificationDAO>(SourceClassificationTable)

    var category by SourceClassificationTable.category
    var subcategory by SourceClassificationTable.subcategory
    var updatedAt by SourceClassificationTable.updated_at
    var deletedAt by SourceClassificationTable.deleted_at
    var serverUpdatedAt by SourceClassificationTable.server_updated_at
}
