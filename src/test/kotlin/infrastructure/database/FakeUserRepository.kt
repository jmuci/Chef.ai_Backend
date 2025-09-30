package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.domain.model.User
import com.tenmilelabs.domain.model.UserWithPassword
import com.tenmilelabs.domain.repository.UserRepository
import kotlinx.datetime.Clock
import java.util.*

class FakeUserRepository : UserRepository {
    private val users = mutableMapOf<UUID, UserWithPassword>()

    companion object {
        // Fixed user ID for test@example.com to match FakeRecipesRepository's "user1"
        val TEST_USER_ID: UUID = UUID.fromString("f81d4fae-7dec-11d0-a765-00a0c91e6bf6")
    }

    override suspend fun createUser(email: String, username: String, passwordHash: String): User? {
        // Check if email already exists
        if (users.values.any { it.email == email }) {
            return null
        }

        // Use fixed ID for test user, random for others
        val userId = if (email == "test@example.com") TEST_USER_ID else UUID.randomUUID()
        val now = Clock.System.now().toString()
        val userWithPassword = UserWithPassword(
            id = userId,
            email = email,
            username = username,
            passwordHash = passwordHash,
            createdAt = now
        )

        users[userId] = userWithPassword

        return User(
            id = userId,
            email = email,
            username = username,
            createdAt = now
        )
    }

    override suspend fun findUserByEmail(email: String): UserWithPassword? {
        return users.values.find { it.email == email }
    }

    override suspend fun findUserById(userId: UUID): User? {
        val userWithPassword = users[userId] ?: return null
        return User(
            id = userWithPassword.id,
            email = userWithPassword.email,
            username = userWithPassword.username,
            createdAt = userWithPassword.createdAt
        )
    }

    // Test helper to clear all users
    fun clear() {
        users.clear()
    }
}
