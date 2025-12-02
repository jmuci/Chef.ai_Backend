package com.tenmilelabs.infrastructure.database.dao

import com.tenmilelabs.infrastructure.database.tables.AllergenTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class AllergenDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AllergenDAO>(AllergenTable)

    var displayName by AllergenTable.display_name
    var updatedAt by AllergenTable.updated_at
    var deletedAt by AllergenTable.deleted_at
    var serverUpdatedAt by AllergenTable.server_updated_at
}
