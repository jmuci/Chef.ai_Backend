package com.tenmilelabs.infrastructure.database.mappers

import com.tenmilelabs.domain.model.User
import com.tenmilelabs.domain.model.UserWithPassword
import com.tenmilelabs.infrastructure.database.dao.UserDAO

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
