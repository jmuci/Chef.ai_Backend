package com.tenmilelabs.domain.service

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.security.SecureRandom
import java.util.*

class JwtService(
    private val secret: String,
    private val issuer: String,
    val audience: String,
    private val expirationMs: Long = 3600_000L * 1, // 1 hour for access tokens
    private val refreshExpirationMs: Long = 3600_000L * 24 * 30 // 30 days for refresh tokens
) {
    val verifier: JWTVerifier = JWT.require(Algorithm.HMAC256(secret))
        .withAudience(audience)
        .withIssuer(issuer)
        .build()

    private val secureRandom = SecureRandom()

    fun generateToken(userId: String, email: String): String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withClaim("type", "access")
            .withJWTId(UUID.randomUUID().toString()) // Unique identifier for each token
            .withIssuedAt(Date(System.currentTimeMillis())) // Issued at time
            .withExpiresAt(Date(System.currentTimeMillis() + expirationMs))
            .sign(Algorithm.HMAC256(secret))
    }

    /**
     * Generate a cryptographically secure random refresh token
     * This is NOT a JWT - it's an opaque token stored hashed in the database
     */
    fun generateRefreshToken(): String {
        val bytes = ByteArray(64) // 512 bits
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Get expiration time in seconds for access tokens
     */
    fun getAccessTokenExpirationSeconds(): Long {
        return expirationMs / 1000
    }

    /**
     * Get expiration time in milliseconds for refresh tokens
     */
    fun getRefreshTokenExpirationMs(): Long {
        return refreshExpirationMs
    }

    fun verifyToken(token: String): String? {
        return try {
            val decodedJWT = verifier.verify(token)
            decodedJWT.getClaim("userId").asString()
        } catch (e: Exception) {
            null
        }
    }
}
