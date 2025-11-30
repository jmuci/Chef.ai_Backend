package com.tenmilelabs.infrastructure.database.dao

import com.tenmilelabs.infrastructure.database.tables.UserTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class UserDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserDAO>(UserTable)

    var displayName by UserTable.displayName
    var email by UserTable.email
    var avatarUrl by UserTable.avatarUrl
    var passwordHash by UserTable.passwordHash
    var createdAt by UserTable.createdAt
    var updatedAt by UserTable.updatedAt
    var deletedAt by UserTable.deletedAt
    var syncState by UserTable.syncState
    var serverUpdatedAt by UserTable.serverUpdatedAt
}
