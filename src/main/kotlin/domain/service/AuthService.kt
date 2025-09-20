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
        // Validate and sanitize inputs
        val validation = InputValidator.validateRegistrationInput(
            request.email,
            request.username,
            request.password
        )

        if (!validation.isValid) {
            log.warn("Registration validation failed: ${validation.errorMessage}")
            return null
        }

        // Sanitize inputs
        val sanitizedEmail = InputValidator.sanitizeEmail(request.email)
        val sanitizedUsername = InputValidator.sanitizeUsername(request.username)

        // Check if user already exists
        val existingUser = userRepository.findUserByEmail(sanitizedEmail)
        if (existingUser != null) {
            log.warn("User already exists with email: $sanitizedEmail")
            return null
        }

        // Hash password
        val passwordHash = hashPassword(request.password)

        // Create user with sanitized inputs
        val user = userRepository.createUser(sanitizedEmail, sanitizedUsername, passwordHash)
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
        // Validate inputs
        val validation = InputValidator.validateLoginInput(request.email, request.password)
        if (!validation.isValid) {
            log.warn("Login validation failed: ${validation.errorMessage}")
            // Use constant-time delay to prevent timing attacks
            simulatePasswordCheck()
            return null
        }

        // Sanitize email
        val sanitizedEmail = InputValidator.sanitizeEmail(request.email)

        // Find user by email
        val user = userRepository.findUserByEmail(sanitizedEmail)
        if (user == null) {
            log.warn("User not found with email: $sanitizedEmail")
            // Use constant-time delay to prevent timing attacks
            simulatePasswordCheck()
            return null
        }

        // Verify password
        if (!verifyPassword(request.password, user.passwordHash)) {
            log.warn("Invalid password for user: $sanitizedEmail")
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

    /**
     * Simulate password check to prevent timing attacks
     * This ensures constant time response whether user exists or not
     */
    private fun simulatePasswordCheck() {
        // Perform a dummy BCrypt hash to match the time of a real password check
        BCrypt.verifyer().verify(
            "dummy-password".toCharArray(),
            "$2a$12\$dummyhashtopreventtimingattack1234567890123456789012"
        )
    }
}
