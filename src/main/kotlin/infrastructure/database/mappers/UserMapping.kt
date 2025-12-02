package com.tenmilelabs.infrastructure.database.mappers

import com.tenmilelabs.domain.model.User
import com.tenmilelabs.domain.model.UserWithPassword
import com.tenmilelabs.infrastructure.database.dao.UserDAO

fun daoToUser(dao: UserDAO): User = User(
    uuid = dao.id.value,
    email = dao.email,
    username = dao.displayName,
    displayName = dao.displayName,
    avatarUrl = dao.avatarUrl,
    createdAt = dao.createdAt.toString(),
    updatedAt = dao.updatedAt.toString()
)

fun daoToUserWithPassword(dao: UserDAO): UserWithPassword = UserWithPassword(
    uuid = dao.id.value,
    email = dao.email,
    username = dao.username,
    passwordHash = dao.passwordHash,
    createdAt = dao.createdAt.toString()
)
