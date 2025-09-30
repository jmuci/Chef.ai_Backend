package com.tenmilelabs.domain.repository

import com.tenmilelabs.domain.model.User
import com.tenmilelabs.domain.model.UserWithPassword
import java.util.*

interface UserRepository {
    suspend fun createUser(email: String, username: String, passwordHash: String): User?
    suspend fun findUserByEmail(email: String): UserWithPassword?
    suspend fun findUserById(userId: UUID): User?
}
