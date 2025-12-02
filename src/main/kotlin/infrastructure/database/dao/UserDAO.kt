package com.tenmilelabs.infrastructure.database.dao

import com.tenmilelabs.infrastructure.database.tables.UserTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class UserDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserDAO>(UserTable)

    var displayName by UserTable.display_name
    var email by UserTable.email
    var passwordHash by UserTable.password_hash
    var avatarUrl by UserTable.avatar_url
    var createdAt by UserTable.created_at
    var updatedAt by UserTable.updated_at
    var deletedAt by UserTable.deleted_at
    var syncState by UserTable.sync_state
    var serverUpdatedAt by UserTable.server_updated_at
}
