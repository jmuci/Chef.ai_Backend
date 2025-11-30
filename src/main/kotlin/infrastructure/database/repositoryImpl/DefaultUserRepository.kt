package com.tenmilelabs.infrastructure.database.repositoryImpl

import com.tenmilelabs.domain.model.User
import com.tenmilelabs.domain.model.UserWithPassword
import com.tenmilelabs.domain.repository.UserRepository
import com.tenmilelabs.infrastructure.database.dao.UserDAO
import com.tenmilelabs.infrastructure.database.mappers.daoToUser
import com.tenmilelabs.infrastructure.database.mappers.daoToUserWithPassword
import com.tenmilelabs.infrastructure.database.mappers.suspendTransaction
import com.tenmilelabs.infrastructure.database.tables.UserTable
import io.ktor.util.logging.*
import java.util.*


class PostgresUserRepository(private val log: Logger) : UserRepository {

    override suspend fun createUser(email: String, username: String, passwordHash: String): User? =
        suspendTransaction {
            try {
                val userDAO = UserDAO.new {
                    this.email = email
                    this.displayName = username
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
