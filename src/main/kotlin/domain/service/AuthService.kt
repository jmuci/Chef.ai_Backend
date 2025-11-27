package com.tenmilelabs.domain.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.tenmilelabs.application.dto.AuthResponse
import com.tenmilelabs.application.dto.LoginRequest
import com.tenmilelabs.application.dto.RegisterRequest
import com.tenmilelabs.domain.model.User
import com.tenmilelabs.infrastructure.database.UserRepository
import io.ktor.util.logging.*

class AuthService(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val log: Logger
) {

    suspend fun register(request: RegisterRequest): AuthResponse? {
        // Validate email format
        if (!isValidEmail(request.email)) {
            log.warn("Invalid email format: ${request.email}")
            return null
        }

        // Validate password strength
        if (!isValidPassword(request.password)) {
            log.warn("Password does not meet requirements")
            return null
        }

        // Check if user already exists
        val existingUser = userRepository.findUserByEmail(request.email)
        if (existingUser != null) {
            log.warn("User already exists with email: ${request.email}")
            return null
        }

        // Hash password
        val passwordHash = hashPassword(request.password)

        // Create user
        val user = userRepository.createUser(request.email, request.username, passwordHash)
            ?: return null

        // Generate JWT token
        val token = jwtService.generateToken(user.id, user.email)

        return AuthResponse(
            token = token,
            userId = user.id,
            username = user.username,
            email = user.email
        )
    }

    suspend fun login(request: LoginRequest): AuthResponse? {
        // Find user by email
        val user = userRepository.findUserByEmail(request.email)
        if (user == null) {
            log.warn("User not found with email: ${request.email}")
            return null
        }

        // Verify password
        if (!verifyPassword(request.password, user.passwordHash)) {
            log.warn("Invalid password for user: ${request.email}")
            return null
        }

        // Generate JWT token
        val token = jwtService.generateToken(user.id, user.email)

        return AuthResponse(
            token = token,
            userId = user.id,
            username = user.username,
            email = user.email
        )
    }

    suspend fun getUserById(userId: String): User? {
        return userRepository.findUserById(userId)
    }

    private fun hashPassword(password: String): String {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray())
    }

    private fun verifyPassword(password: String, hash: String): Boolean {
        return BCrypt.verifyer().verify(password.toCharArray(), hash).verified
    }

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return email.matches(emailRegex)
    }

    private fun isValidPassword(password: String): Boolean {
        // At least 8 characters, contains letter and number
        return password.length >= 8 &&
                password.any { it.isLetter() } &&
                password.any { it.isDigit() }
    }
}
