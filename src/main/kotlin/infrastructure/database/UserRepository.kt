package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.domain.model.User
import com.tenmilelabs.domain.model.UserWithPassword
import io.ktor.util.logging.*
import java.util.*

interface UserRepository {
    suspend fun createUser(email: String, username: String, passwordHash: String): User?
    suspend fun findUserByEmail(email: String): UserWithPassword?
    suspend fun findUserById(userId: UUID): User?
}

class PostgresUserRepository(private val log: Logger) : UserRepository {

    override suspend fun createUser(email: String, username: String, passwordHash: String): User? =
        suspendTransaction {
            try {
                val userDAO = UserDAO.new {
                    this.email = email
                    this.username = username
                    this.passwordHash = passwordHash
                }
                daoToUser(userDAO)
            } catch (ex: Exception) {
                log.error("Failed to create user with email: $email", ex)
                null
            }
        }

    override suspend fun findUserByEmail(email: String): UserWithPassword? = suspendTransaction {
        UserDAO
            .find { UserTable.email eq email }
            .limit(1)
            .map(::daoToUserWithPassword)
            .firstOrNull()
    }

    override suspend fun findUserById(userId: UUID): User? = suspendTransaction {
        try {
            UserDAO
                .findById(userId)
                ?.let(::daoToUser)
        } catch (ex: Exception) {
            log.error("Failed to find user by id: $userId", ex)
            null
        }
    }
}
