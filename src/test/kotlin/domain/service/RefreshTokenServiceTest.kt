package com.tenmilelabs.domain.service

import com.tenmilelabs.application.dto.RefreshTokenRequest
import com.tenmilelabs.application.dto.RegisterRequest
import com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository
import com.tenmilelabs.infrastructure.database.FakeUserRepository
import io.ktor.util.logging.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.*
import com.tenmilelabs.domain.exception.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RefreshTokenServiceTest {

    private lateinit var userRepository: FakeUserRepository
    private lateinit var refreshTokenRepository: FakeRefreshTokenRepository
    private lateinit var jwtService: JwtService
    private lateinit var authService: AuthService
    private val logger = KtorSimpleLogger("RefreshTokenServiceTest")

    @BeforeEach
    fun setup() {
        userRepository = FakeUserRepository()
        refreshTokenRepository = FakeRefreshTokenRepository()
        jwtService = JwtService(
            secret = "test-secret-key-for-jwt-tokens",
            issuer = "http://test.com",
            audience = "test-audience"
        )
        authService = AuthService(userRepository, refreshTokenRepository, jwtService, logger)
    }

    @Test
    fun `refresh token returns new tokens`() = runTest {
        // Register a user
        val registerRequest = RegisterRequest(
            email = "refresh@example.com",
            username = "refreshuser",
            password = "SecurePass123"
        )
        val authResponse = authService.register(registerRequest)
        assertNotNull(authResponse)

        val originalRefreshToken = authResponse.refreshToken
        val originalAccessToken = authResponse.token

        // Refresh the token
        val refreshRequest = RefreshTokenRequest(originalRefreshToken)
        val refreshResponse = authService.refreshToken(refreshRequest)

        assertNotNull(refreshResponse, "Refresh response should not be null")
        assertNotNull(refreshResponse.accessToken, "Access token should not be null")
        assertNotNull(refreshResponse.refreshToken, "Refresh token should not be null")
        assertEquals(authResponse.userId, refreshResponse.userId)

        // New tokens should be different from original
        assertTrue(
            refreshResponse.accessToken != originalAccessToken,
            "Access token should be different: old=$originalAccessToken, new=${refreshResponse.accessToken}"
        )
        assertTrue(
            refreshResponse.refreshToken != originalRefreshToken,
            "Refresh token should be different: old=$originalRefreshToken, new=${refreshResponse.refreshToken}")
    }

    @Test
    fun `refresh token with invalid token throws exception`() = runTest {
        val refreshRequest = RefreshTokenRequest("invalid-token")
        assertFailsWith<InvalidRefreshTokenException> {
            authService.refreshToken(refreshRequest)
        }
    }

    @Test
    fun `refresh token with blank token throws exception`() = runTest {
        val refreshRequest = RefreshTokenRequest("")
        assertFailsWith<InvalidRefreshTokenException> {
            authService.refreshToken(refreshRequest)
        }
    }

    @Test
    fun `reused refresh token is rejected and all tokens revoked`() = runTest {
        // Register a user
        val registerRequest = RegisterRequest(
            email = "reuse@example.com",
            username = "reuseuser",
            password = "SecurePass123"
        )
        val authResponse = authService.register(registerRequest)
        assertNotNull(authResponse)

        val originalRefreshToken = authResponse.refreshToken

        // First refresh - should succeed
        val refreshRequest1 = RefreshTokenRequest(originalRefreshToken)
        val refreshResponse1 = authService.refreshToken(refreshRequest1)
        assertNotNull(refreshResponse1)

        val newRefreshToken = refreshResponse1.refreshToken

        // Try to reuse the original token - should throw TokenReuseDetectedException
        val refreshRequest2 = RefreshTokenRequest(originalRefreshToken)
        assertFailsWith<TokenReuseDetectedException>("Reused token should be rejected") {
            authService.refreshToken(refreshRequest2)
        }

        // Even the new token should now be revoked (security breach detected)
        val refreshRequest3 = RefreshTokenRequest(newRefreshToken)
        assertFailsWith<TokenReuseDetectedException>("All tokens should be revoked after reuse detection") {
            authService.refreshToken(refreshRequest3)
        }
    }

    @Test
    fun `expired refresh token is rejected`() = runTest {
        // Register a user first
        val registerRequest = RegisterRequest(
            email = "expired@example.com",
            username = "expireduser",
            password = "SecurePass123"
        )
        val authResponse = authService.register(registerRequest)
        assertNotNull(authResponse)

        val userId = UUID.fromString(authResponse.userId)
        val expiredTokenString = jwtService.generateRefreshToken()

        // Manually create an expired token in the repository
        // Set expiration to 1 second in the past
        val now = Clock.System.now()
        val pastTime = now.minus(1.seconds)
        val expiredTokenHash = MessageDigest.getInstance("SHA-256")
            .digest(expiredTokenString.toByteArray())
            .let { Base64.getEncoder().encodeToString(it) }

        refreshTokenRepository.createRefreshToken(
            userId = userId,
            tokenHash = expiredTokenHash,
            expiresAt = pastTime
        )

        // Try to use the expired token - should throw exception
        val refreshRequest = RefreshTokenRequest(expiredTokenString)
        assertFailsWith<InvalidRefreshTokenException>("Expired token should be rejected") {
            authService.refreshToken(refreshRequest)
        }
    }

    @Test
    fun `refresh token for deleted user is rejected`() = runTest {
        // This test simulates a user being deleted after token issuance
        // In this test, we'll manually create a token for a non-existent user

        // Register and then "delete" the user by clearing the repository
        val registerRequest = RegisterRequest(
            email = "deleted@example.com",
            username = "deleteduser",
            password = "SecurePass123"
        )
        val authResponse = authService.register(registerRequest)
        assertNotNull(authResponse)

        val refreshToken = authResponse.refreshToken

        // "Delete" the user
        userRepository.clear()

        // Try to refresh - should throw UserNotFoundException
        val refreshRequest = RefreshTokenRequest(refreshToken)
        assertFailsWith<UserNotFoundException>("Token for deleted user should be rejected") {
            authService.refreshToken(refreshRequest)
        }
    }

    @Test
    fun `multiple refresh token rotations work correctly`() = runTest {
        // Register a user
        val registerRequest = RegisterRequest(
            email = "rotation@example.com",
            username = "rotationuser",
            password = "SecurePass123"
        )
        val authResponse = authService.register(registerRequest)
        assertNotNull(authResponse)

        var currentRefreshToken = authResponse.refreshToken
        var currentAccessToken = authResponse.token

        // Perform multiple rotations
        repeat(5) { iteration ->
            val refreshRequest = RefreshTokenRequest(currentRefreshToken)
            val refreshResponse = authService.refreshToken(refreshRequest)

            assertNotNull(refreshResponse, "Rotation $iteration should succeed")
            assertNotNull(refreshResponse.accessToken, "Access token should not be null at iteration $iteration")
            assertNotNull(refreshResponse.refreshToken, "Refresh token should not be null at iteration $iteration")

            // Verify tokens changed
            assertTrue(
                refreshResponse.accessToken != currentAccessToken,
                "Access token should change at iteration $iteration: old=$currentAccessToken, new=${refreshResponse.accessToken}"
            )
            assertTrue(
                refreshResponse.refreshToken != currentRefreshToken,
                "Refresh token should change at iteration $iteration: old=$currentRefreshToken, new=${refreshResponse.refreshToken}")

            // Update for next iteration
            currentRefreshToken = refreshResponse.refreshToken
            currentAccessToken = refreshResponse.accessToken
        }
    }

    @Test
    fun `revoke all user tokens works`() = runTest {
        // Register a user
        val registerRequest = RegisterRequest(
            email = "revokeall@example.com",
            username = "revokealluser",
            password = "SecurePass123"
        )
        val authResponse = authService.register(registerRequest)
        assertNotNull(authResponse)

        // Create a few more tokens by logging in
        val userId = authResponse.userId

        // Revoke all tokens
        val revokedCount = authService.revokeAllUserTokens(UUID.fromString(userId))
        assertTrue(revokedCount >= 1, "Should have revoked at least one token")

        // Try to use the revoked token - should throw TokenReuseDetectedException
        // (because revoked tokens trigger the reuse detection logic)
        val refreshRequest = RefreshTokenRequest(authResponse.refreshToken)
        assertFailsWith<TokenReuseDetectedException>("Revoked token should trigger reuse detection") {
            authService.refreshToken(refreshRequest)
        }
    }

    @Test
    fun `access token contains correct claims`() = runTest {
        // Register a user
        val registerRequest = RegisterRequest(
            email = "claims@example.com",
            username = "claimsuser",
            password = "SecurePass123"
        )
        val authResponse = authService.register(registerRequest)
        assertNotNull(authResponse)

        // Verify we can decode the userId from the access token
        val userId = jwtService.verifyToken(authResponse.token)
        assertNotNull(userId)
        assertEquals(authResponse.userId, userId)
    }

    @Test
    fun `refreshed access token contains correct claims`() = runTest {
        // Register a user
        val registerRequest = RegisterRequest(
            email = "refreshclaims@example.com",
            username = "refreshclaimsuser",
            password = "SecurePass123"
        )
        val authResponse = authService.register(registerRequest)
        assertNotNull(authResponse)

        // Refresh
        val refreshRequest = RefreshTokenRequest(authResponse.refreshToken)
        val refreshResponse = authService.refreshToken(refreshRequest)
        assertNotNull(refreshResponse)

        // Verify the new access token has correct claims
        val userId = jwtService.verifyToken(refreshResponse.accessToken)
        assertNotNull(userId)
        assertEquals(refreshResponse.userId, userId)
    }

    @Test
    fun `refresh token generation is cryptographically random`() = runTest {
        val tokens = mutableSetOf<String>()

        // Generate 100 tokens
        repeat(100) {
            val token = jwtService.generateRefreshToken()
            assertNotNull(token)
            assertTrue(token.isNotBlank())
            tokens.add(token)
        }

        // All should be unique
        assertEquals(100, tokens.size, "All generated tokens should be unique")

        // Should be reasonably long (base64 encoding of 64 bytes)
        val averageLength = tokens.map { it.length }.average()
        assertTrue(averageLength > 80, "Tokens should be long enough to be secure")
    }
}
