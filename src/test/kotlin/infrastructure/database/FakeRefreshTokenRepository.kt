package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.domain.model.RefreshToken
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.*

class FakeRefreshTokenRepository : RefreshTokenRepository {
    private val tokens = mutableMapOf<String, RefreshToken>()

    override suspend fun createRefreshToken(userId: UUID, tokenHash: String, expiresAt: Instant): RefreshToken {
        val tokenId = UUID.randomUUID().toString()
        val now = Clock.System.now()
        val token = RefreshToken(
            id = tokenId,
            userId = userId,
            tokenHash = tokenHash,
            expiresAt = expiresAt.toString(),
            createdAt = now.toString(),
            isRevoked = false,
            revokedAt = null
        )
        tokens[tokenId] = token
        return token
    }

    override suspend fun findByTokenHash(tokenHash: String): RefreshToken? {
        return tokens.values.find { it.tokenHash == tokenHash }
    }

    override suspend fun revokeToken(tokenId: String): Boolean {
        val token = tokens[tokenId]
        return if (token != null) {
            tokens[tokenId] = token.copy(
                isRevoked = true,
                revokedAt = Clock.System.now().toString()
            )
            true
        } else {
            false
        }
    }

    override suspend fun revokeAllUserTokens(userId: UUID): Int {
        val now = Clock.System.now().toString()
        var count = 0
        tokens.values.filter { it.userId == userId && !it.isRevoked }.forEach { token ->
            tokens[token.id] = token.copy(
                isRevoked = true,
                revokedAt = now
            )
            count++
        }
        return count
    }

    override suspend fun deleteExpiredTokens(): Int {
        val now = Clock.System.now()
        val expired = tokens.values.filter {
            Instant.parse(it.expiresAt) < now
        }
        expired.forEach { tokens.remove(it.id) }
        return expired.size
    }

    // Test helper
    fun clear() {
        tokens.clear()
    }
}
