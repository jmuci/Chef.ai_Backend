package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.domain.model.User
import com.tenmilelabs.domain.model.UserWithPassword
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.*

//TODO break into 3 different files
object UserTable : UUIDTable("users", "id") {
    val email = varchar("email", 255).uniqueIndex()
    val username = varchar("username", 100)
    val passwordHash = varchar("password_hash", 255)
    val createdAt = timestamp("created_at").clientDefault { kotlinx.datetime.Clock.System.now() }
}

class UserDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserDAO>(UserTable)

    var email by UserTable.email
    var username by UserTable.username
    var passwordHash by UserTable.passwordHash
    var createdAt by UserTable.createdAt
}

fun daoToUser(dao: UserDAO): User = User(
    id = dao.id.value,
    email = dao.email,
    username = dao.username,
    createdAt = dao.createdAt.toString()
)

fun daoToUserWithPassword(dao: UserDAO): UserWithPassword = UserWithPassword(
    id = dao.id.value,
    email = dao.email,
    username = dao.username,
    passwordHash = dao.passwordHash,
    createdAt = dao.createdAt.toString()
)
