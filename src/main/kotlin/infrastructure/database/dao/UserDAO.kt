package com.tenmilelabs.infrastructure.database.dao

import com.tenmilelabs.infrastructure.database.tables.UserTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class UserDAO(uuid: EntityID<UUID>) : UUIDEntity(uuid) {
    companion object : UUIDEntityClass<UserDAO>(UserTable)
    var username by UserTable.user_name
    var displayName by UserTable.display_name
    var email by UserTable.email
    var passwordHash by UserTable.password_hash
    var avatarUrl by UserTable.avatar_url
    var createdAt by UserTable.created_at
    var updatedAt by UserTable.updated_at
}
