package com.tenmilelabs.domain.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.tenmilelabs.application.dto.*
import com.tenmilelabs.domain.exception.*
import com.tenmilelabs.domain.model.User
import com.tenmilelabs.infrastructure.database.RefreshTokenRepository
import com.tenmilelabs.infrastructure.database.UserRepository
import io.ktor.util.logging.*
import kotlinx.datetime.Clock
import java.security.MessageDigest
import java.util.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtService: JwtService,
    private val log: Logger
) {

    suspend fun register(request: RegisterRequest): AuthResponse {
        // Validate and sanitize inputs
        val validation = InputValidator.validateRegistrationInput(
            request.email,
            request.username,
            request.password
        )

        if (!validation.isValid) {
            log.warn("Registration validation failed: ${validation.errorMessage}")
            throw ValidationException(validation.errorMessage ?: "Invalid input")
        }

        // Sanitize inputs
        val sanitizedEmail = InputValidator.sanitizeEmail(request.email)
        val sanitizedUsername = InputValidator.sanitizeUsername(request.username)

        // Check if user already exists
        val existingUser = userRepository.findUserByEmail(sanitizedEmail)
        if (existingUser != null) {
            log.warn("User already exists with email: $sanitizedEmail")
            throw UserAlreadyExistsException("User with email $sanitizedEmail already exists")
        }

        // Hash password
        val passwordHash = hashPassword(request.password)

        // Create user with sanitized inputs
        val user = userRepository.createUser(sanitizedEmail, sanitizedUsername, passwordHash)
            ?: throw AuthInternalException("Failed to create user")

        // Generate and store tokens
        return generateAndStoreTokens(user.id.toString(), user.email, user.username)
    }

    suspend fun login(request: LoginRequest): AuthResponse {
        // Validate inputs
        val validation = InputValidator.validateLoginInput(request.email, request.password)
        if (!validation.isValid) {
            log.warn("Login validation failed: ${validation.errorMessage}")
            // Use constant-time delay to prevent timing attacks
            simulatePasswordCheck()
            throw ValidationException(validation.errorMessage ?: "Invalid input")
        }

        // Sanitize email
        val sanitizedEmail = InputValidator.sanitizeEmail(request.email)

        // Find user by email
        val user = userRepository.findUserByEmail(sanitizedEmail)
        if (user == null) {
            log.warn("User not found with email: $sanitizedEmail")
            // Use constant-time delay to prevent timing attacks
            simulatePasswordCheck()
            throw InvalidCredentialsException("Invalid email or password")
        }

        // Verify password
        if (!verifyPassword(request.password, user.passwordHash)) {
            log.warn("Invalid password for user: $sanitizedEmail")
            throw InvalidCredentialsException("Invalid email or password")
        }

        // Generate and store tokens
        return generateAndStoreTokens(user.id.toString(), user.email, user.username)
    }

    suspend fun getUserById(userId: UUID): User? {
        return userRepository.findUserById(userId)
    }

    /**
     * Refresh access token using a refresh token
     * Implements token rotation: invalidates old refresh token and issues new ones
     * Throws InvalidRefreshTokenException for invalid/expired tokens
     * Throws TokenReuseDetectedException if token reuse is detected
     * Throws UserNotFoundException if user no longer exists
     * Throws AuthInternalException for internal failures
     */
    suspend fun refreshToken(request: RefreshTokenRequest): RefreshTokenResponse {
        // Validate input
        if (request.refreshToken.isBlank()) {
            log.warn("Refresh token is blank")
            throw InvalidRefreshTokenException("Refresh token cannot be blank")
        }

        // Hash the incoming refresh token to look it up in database
        val tokenHash = hashRefreshToken(request.refreshToken)

        // Find the refresh token in database
        val storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
            ?: run {
                log.warn("Refresh token not found in database")
                throw InvalidRefreshTokenException("Invalid refresh token")
            }

        // Check if token is revoked (token reuse detected)
        if (storedToken.isRevoked) {
            log.warn("Attempted reuse of revoked refresh token for user: ${storedToken.userId}")
            // Token reuse detected - revoke all tokens for this user (security breach)
            refreshTokenRepository.revokeAllUserTokens(storedToken.userId)
            throw TokenReuseDetectedException("Token reuse detected. All sessions have been terminated for security.")
        }

        // Check if token is expired
        val now = Clock.System.now()
        val expiresAt = kotlinx.datetime.Instant.parse(storedToken.expiresAt)
        if (now >= expiresAt) {
            log.warn("Refresh token expired for user: ${storedToken.userId}")
            refreshTokenRepository.revokeToken(storedToken.id)
            throw InvalidRefreshTokenException("Refresh token has expired")
        }

        // Get user information
        val user = userRepository.findUserById(storedToken.userId)
            ?: run {
                log.warn("User not found for refresh token: ${storedToken.userId}")
                refreshTokenRepository.revokeToken(storedToken.id)
                throw UserNotFoundException("User not found")
            }

        // Generate new tokens BEFORE revoking old one
        // This ensures we have the new token ready before invalidating the old one
        val newAccessToken = jwtService.generateToken(user.id.toString(), user.email)
        val newRefreshTokenString = jwtService.generateRefreshToken()
        val newRefreshTokenHash = hashRefreshToken(newRefreshTokenString)

        // Store new refresh token FIRST
        val duration = jwtService.getRefreshTokenExpirationMs().toDuration(DurationUnit.MILLISECONDS)
        val refreshExpiresAt = now.plus(duration)
        refreshTokenRepository.createRefreshToken(
            userId = user.id,
            tokenHash = newRefreshTokenHash,
            expiresAt = refreshExpiresAt
        ) ?: throw AuthInternalException("Failed to store new refresh token for user: ${user.id}")

        // Token rotation: Revoke the old token AFTER new one is safely stored
        // If revocation fails, the new token is already valid, so return success anyway
        val revoked = refreshTokenRepository.revokeToken(storedToken.id)
        if (!revoked) {
            log.warn("Failed to revoke old refresh token, but new token was created successfully")
            // Continue - new token works, old token will be cleaned up eventually
        }

        log.info("Successfully refreshed tokens for user: ${user.id}")

        return RefreshTokenResponse(
            accessToken = newAccessToken,
            refreshToken = newRefreshTokenString,
            userId = user.id.toString(),
            expiresIn = jwtService.getAccessTokenExpirationSeconds()
        )
    }

    /**
     * Revoke all refresh tokens for a user (useful for logout from all devices)
     */
    suspend fun revokeAllUserTokens(userId: UUID): Int {
        return refreshTokenRepository.revokeAllUserTokens(userId)
    }

    /**
     * Generate and store both access and refresh tokens for a user
     * Returns AuthResponse with tokens
     * Throws AuthInternalException if token storage fails
     *
     * Accepts both User and UserWithPassword types since they share the same essential fields
     */
    private suspend fun generateAndStoreTokens(
        userId: String,
        email: String,
        username: String
    ): AuthResponse {
        // Generate tokens
        val accessToken = jwtService.generateToken(userId, email)
        val refreshTokenString = jwtService.generateRefreshToken()
        val refreshTokenHash = hashRefreshToken(refreshTokenString)

        // Store refresh token
        val now = Clock.System.now()
        val duration = jwtService.getRefreshTokenExpirationMs().toDuration(DurationUnit.MILLISECONDS)
        val refreshExpiresAt = now.plus(duration)
        refreshTokenRepository.createRefreshToken(
            userId = UUID.fromString(userId),
            tokenHash = refreshTokenHash,
            expiresAt = refreshExpiresAt
        ) ?: throw AuthInternalException("Failed to store refresh token for user: $userId")

        return AuthResponse(
            token = accessToken,
            refreshToken = refreshTokenString,
            userId = userId,
            username = username,
            email = email,
            expiresIn = jwtService.getAccessTokenExpirationSeconds()
        )
    }

    private fun hashPassword(password: String): String {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray())
    }

    private fun verifyPassword(password: String, hash: String): Boolean {
        return BCrypt.verifyer().verify(password.toCharArray(), hash).verified
    }

    /**
     * Hash refresh token using SHA-256
     * We use SHA-256 instead of BCrypt because:
     * 1. Refresh tokens are 64+ bytes (BCrypt limit is 72 bytes)
     * 2. Refresh tokens are cryptographically random (high entropy)
     * 3. We don't need BCrypt's slow hashing for already-secure random tokens
     * 4. SHA-256 is fast and one-way, which is sufficient for token verification
     */
    private fun hashRefreshToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(token.toByteArray())
        return Base64.getEncoder().encodeToString(hashBytes)
    }

    /**
     * Simulate password check to prevent timing attacks
     * This ensures constant time response whether user exists or not
     */
    private fun simulatePasswordCheck() {
        try {
            // Perform a dummy BCrypt hash to match the time of a real password check
            // Using a valid pre-computed BCrypt hash of "dummy-password"
            BCrypt.verifyer().verify(
                "wrong-password".toCharArray(),
                "\$2a\$12\$LQDHPwzzPWFERYtY3KfQaeFmIaM5b7YQ7GxJYqG.N7GnXvLqYG5Ai" // Hash of "dummy-password"
            )
        } catch (e: Exception) {
            // If somehow the dummy verification fails, we still want to consume time
            // Fall back to hashing a dummy password
            BCrypt.withDefaults().hashToString(12, "dummy-password".toCharArray())
        }
    }
}
