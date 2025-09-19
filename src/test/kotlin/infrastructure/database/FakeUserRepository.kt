package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.domain.model.User
import com.tenmilelabs.domain.model.UserWithPassword
import kotlinx.datetime.Clock
import java.util.*

class FakeUserRepository : UserRepository {
    private val users = mutableMapOf<String, UserWithPassword>()

    companion object {
        // Fixed user ID for test@example.com to match FakeRecipesRepository's "user1"
        const val TEST_USER_ID = "user1"
    }

    override suspend fun createUser(email: String, username: String, passwordHash: String): User? {
        // Check if email already exists
        if (users.values.any { it.email == email }) {
            return null
        }

        // Use fixed ID for test user, random for others
        val userId = if (email == "test@example.com") TEST_USER_ID else UUID.randomUUID().toString()
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

    override suspend fun findUserById(userId: String): User? {
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
