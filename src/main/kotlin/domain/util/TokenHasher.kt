package com.tenmilelabs.domain.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Shared primitives for opaque, high-entropy tokens (household invites today; refresh tokens
 * follow the identical hand-rolled pattern in [com.tenmilelabs.domain.service.JwtService] and
 * [com.tenmilelabs.domain.service.AuthService] — see PR B6 for folding those onto this object).
 *
 * The token itself is returned to the caller exactly once and never persisted; only its hash is
 * stored, so a database read can never recover a usable token.
 */
object TokenHasher {

    private val secureRandom = SecureRandom()

    /**
     * A cryptographically random, URL-safe token with [byteLength] bytes of entropy (32 = 256
     * bits, ample for an invite token that's also rate-limited on accept).
     */
    fun generateSecureToken(byteLength: Int = 32): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * SHA-256 of [token], base64-encoded. Same choice [AuthService.hashRefreshToken] documents:
     * the token is already cryptographically random and high-entropy, so a slow password hash
     * (BCrypt) buys nothing — a fast one-way digest is sufficient and avoids BCrypt's 72-byte
     * input cap.
     */
    fun sha256Base64(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(token.toByteArray())
        return Base64.getEncoder().encodeToString(hashBytes)
    }
}
