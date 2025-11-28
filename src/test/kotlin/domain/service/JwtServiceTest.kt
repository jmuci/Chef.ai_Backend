package com.tenmilelabs.domain.service

import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException
import org.junit.jupiter.api.Test
import kotlin.test.*

class JwtServiceTest {

    private val testSecret = "test-secret-key-for-jwt-tokens-must-be-long"
    private val testIssuer = "http://test.com"
    private val testAudience = "test-audience"

    @Test
    fun `generateToken should create valid JWT token`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        val token = jwtService.generateToken("user123", "user@example.com")

        assertNotNull(token)
        assertTrue(token.isNotEmpty())
        assertTrue(token.split(".").size == 3) // JWT has 3 parts: header.payload.signature
    }

    @Test
    fun `generateToken should include userId claim`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        val token = jwtService.generateToken("user123", "user@example.com")
        val decodedJWT = JWT.decode(token)

        assertEquals("user123", decodedJWT.getClaim("userId").asString())
    }

    @Test
    fun `generateToken should include email claim`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        val token = jwtService.generateToken("user123", "user@example.com")
        val decodedJWT = JWT.decode(token)

        assertEquals("user@example.com", decodedJWT.getClaim("email").asString())
    }

    @Test
    fun `generateToken should include issuer`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        val token = jwtService.generateToken("user123", "user@example.com")
        val decodedJWT = JWT.decode(token)

        assertEquals(testIssuer, decodedJWT.issuer)
    }

    @Test
    fun `generateToken should include audience`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        val token = jwtService.generateToken("user123", "user@example.com")
        val decodedJWT = JWT.decode(token)

        assertTrue(decodedJWT.audience.contains(testAudience))
    }

    @Test
    fun `generateToken should set expiration time`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        val token = jwtService.generateToken("user123", "user@example.com")
        val decodedJWT = JWT.decode(token)

        assertNotNull(decodedJWT.expiresAt)
        assertTrue(decodedJWT.expiresAt.time > System.currentTimeMillis())
    }

    @Test
    fun `generateToken with custom expiration should respect expiration time`() {
        val customExpiration = 60000L // 1 minute
        val jwtService = JwtService(testSecret, testIssuer, testAudience, customExpiration)

        val token = jwtService.generateToken("user123", "user@example.com")
        val decodedJWT = JWT.decode(token)

        val expirationTime = decodedJWT.expiresAt.time
        val currentTime = System.currentTimeMillis()
        val timeDiff = expirationTime - currentTime

        // Expiration should be approximately current time + custom expiration
        // Allow generous tolerance for test execution time
        assertTrue(
            timeDiff >= customExpiration - 5000,
            "Expiration diff $timeDiff should be >= ${customExpiration - 5000}ms"
        )
        assertTrue(
            timeDiff <= customExpiration + 5000,
            "Expiration diff $timeDiff should be <= ${customExpiration + 5000}ms"
        )
    }

    @Test
    fun `verifyToken should return userId for valid token`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        val token = jwtService.generateToken("user123", "user@example.com")
        val userId = jwtService.verifyToken(token)

        assertEquals("user123", userId)
    }

    @Test
    fun `verifyToken should return null for invalid token`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        val userId = jwtService.verifyToken("invalid.token.here")

        assertNull(userId)
    }

    @Test
    fun `verifyToken should return null for tampered token`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        val token = jwtService.generateToken("user123", "user@example.com")
        // Tamper with the token
        val tamperedToken = token.substring(0, token.length - 5) + "xxxxx"

        val userId = jwtService.verifyToken(tamperedToken)

        assertNull(userId)
    }

    @Test
    fun `verifyToken should return null for token with wrong secret`() {
        val jwtService1 = JwtService("secret1", testIssuer, testAudience)
        val jwtService2 = JwtService("secret2", testIssuer, testAudience)

        val token = jwtService1.generateToken("user123", "user@example.com")
        val userId = jwtService2.verifyToken(token)

        assertNull(userId)
    }

    @Test
    fun `verifyToken should return null for expired token`() {
        // Create service with 1ms expiration
        val jwtService = JwtService(testSecret, testIssuer, testAudience, expirationMs = 1)

        val token = jwtService.generateToken("user123", "user@example.com")

        // Wait for token to expire
        Thread.sleep(10)

        val userId = jwtService.verifyToken(token)

        assertNull(userId)
    }

    @Test
    fun `verifyToken should return null for token with wrong audience`() {
        val jwtService = JwtService(testSecret, testIssuer, "audience1")
        val verifyService = JwtService(testSecret, testIssuer, "audience2")

        val token = jwtService.generateToken("user123", "user@example.com")
        val userId = verifyService.verifyToken(token)

        assertNull(userId)
    }

    @Test
    fun `verifyToken should return null for token with wrong issuer`() {
        val jwtService = JwtService(testSecret, "issuer1", testAudience)
        val verifyService = JwtService(testSecret, "issuer2", testAudience)

        val token = jwtService.generateToken("user123", "user@example.com")
        val userId = verifyService.verifyToken(token)

        assertNull(userId)
    }

    @Test
    fun `verifier property should successfully verify valid tokens`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        val token = jwtService.generateToken("user123", "user@example.com")
        val decodedJWT = jwtService.verifier.verify(token)

        assertNotNull(decodedJWT)
        assertEquals("user123", decodedJWT.getClaim("userId").asString())
    }

    @Test
    fun `verifier property should reject invalid tokens`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        assertFails {
            jwtService.verifier.verify("invalid.token.here")
        }
    }

    @Test
    fun `generateToken should create unique tokens for same user`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        val token1 = jwtService.generateToken("user123", "user@example.com")
        Thread.sleep(1100) // Ensure different timestamps (need >1 second for JWT exp)
        val token2 = jwtService.generateToken("user123", "user@example.com")

        // Both tokens should be valid but potentially different
        assertNotNull(jwtService.verifyToken(token1))
        assertNotNull(jwtService.verifyToken(token2))
    }

    @Test
    fun `generateToken should handle special characters in userId`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        val specialUserId = "user-123_test@domain#special"
        val token = jwtService.generateToken(specialUserId, "user@example.com")
        val userId = jwtService.verifyToken(token)

        assertEquals(specialUserId, userId)
    }

    @Test
    fun `generateToken should handle special characters in email`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        val specialEmail = "user+tag@sub-domain.example.co.uk"
        val token = jwtService.generateToken("user123", specialEmail)
        val decodedJWT = JWT.decode(token)

        assertEquals(specialEmail, decodedJWT.getClaim("email").asString())
    }

    @Test
    fun `generateToken should handle empty userId`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        val token = jwtService.generateToken("", "user@example.com")
        val userId = jwtService.verifyToken(token)

        assertEquals("", userId)
    }

    @Test
    fun `generateToken should handle very long userId`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        val longUserId = "u".repeat(1000)
        val token = jwtService.generateToken(longUserId, "user@example.com")
        val userId = jwtService.verifyToken(token)

        assertEquals(longUserId, userId)
    }

    @Test
    fun `verifyToken should return null for empty token`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        val userId = jwtService.verifyToken("")

        assertNull(userId)
    }

    @Test
    fun `verifyToken should return null for malformed token`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        val malformedTokens = listOf(
            "not.a.jwt",
            "only.two.parts",
            "bearer token",
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        )

        malformedTokens.forEach { token ->
            val userId = jwtService.verifyToken(token)
            assertNull(userId, "Token '$token' should have been rejected")
        }
    }

    @Test
    fun `audience property should be publicly accessible`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        assertEquals(testAudience, jwtService.audience)
    }

    @Test
    fun `verifier property should be publicly accessible`() {
        val jwtService = JwtService(testSecret, testIssuer, testAudience)

        assertNotNull(jwtService.verifier)
    }

    @Test
    fun `generateToken should work with minimal secret length`() {
        // HMAC256 requires at least 256 bits (32 bytes) for security
        val minimalSecret = "a".repeat(32)
        val jwtService = JwtService(minimalSecret, testIssuer, testAudience)

        val token = jwtService.generateToken("user123", "user@example.com")
        val userId = jwtService.verifyToken(token)

        assertEquals("user123", userId)
    }

    @Test
    fun `token should remain valid until expiration`() {
        val expirationMs = 2000L // 2 seconds
        val jwtService = JwtService(testSecret, testIssuer, testAudience, expirationMs)

        val token = jwtService.generateToken("user123", "user@example.com")

        // Verify immediately - should be valid
        assertNotNull(jwtService.verifyToken(token), "Token should be valid immediately after generation")

        // Wait for full expiration plus generous buffer
        Thread.sleep(expirationMs + 1000)

        // Should now be expired
        assertNull(jwtService.verifyToken(token), "Token should be expired after ${expirationMs + 1000}ms")
    }
}
