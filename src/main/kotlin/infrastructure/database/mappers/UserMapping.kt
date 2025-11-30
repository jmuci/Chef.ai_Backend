package com.tenmilelabs.infrastructure.database.mappers

import com.tenmilelabs.domain.model.User
import com.tenmilelabs.domain.model.UserWithPassword
import com.tenmilelabs.infrastructure.database.dao.UserDAO

fun daoToUser(dao: UserDAO): User = User(
    id = dao.id.value,
    email = dao.email,
    displayName = dao.displayName,
    avatarUrl = dao.avatarUrl,
    createdAt = dao.createdAt.toString(),
    updatedAt = dao.updatedAt.toString()
)

fun daoToUserWithPassword(dao: UserDAO): UserWithPassword = UserWithPassword(
    id = dao.id.value,
    email = dao.email,
    displayName = dao.displayName,
    passwordHash = dao.passwordHash,
    createdAt = dao.createdAt.toString()
)
