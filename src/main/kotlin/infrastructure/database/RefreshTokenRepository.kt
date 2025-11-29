package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.domain.model.RefreshToken
import io.ktor.util.logging.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import java.util.*

interface RefreshTokenRepository {
    suspend fun createRefreshToken(userId: UUID, tokenHash: String, expiresAt: Instant): RefreshToken?
    suspend fun findByTokenHash(tokenHash: String): RefreshToken?
    suspend fun revokeToken(tokenId: String): Boolean
    suspend fun revokeAllUserTokens(userId: UUID): Int
    suspend fun deleteExpiredTokens(): Int
}

class PostgresRefreshTokenRepository(private val log: Logger) : RefreshTokenRepository {

    override suspend fun createRefreshToken(userId: UUID, tokenHash: String, expiresAt: Instant): RefreshToken? =
        suspendTransaction {
            try {
                val tokenDAO = RefreshTokenDAO.new {
                    this.userId = userId
                    this.tokenHash = tokenHash
                    this.expiresAt = expiresAt
                }
                daoToRefreshToken(tokenDAO)
            } catch (ex: Exception) {
                log.error("Failed to create refresh token for user: $userId", ex)
                null
            }
        }

    override suspend fun findByTokenHash(tokenHash: String): RefreshToken? = suspendTransaction {
        try {
            RefreshTokenDAO
                .find { RefreshTokenTable.tokenHash eq tokenHash }
                .limit(1)
                .map(::daoToRefreshToken)
                .firstOrNull()
        } catch (ex: Exception) {
            log.error("Failed to find refresh token", ex)
            null
        }
    }

    override suspend fun revokeToken(tokenId: String): Boolean = suspendTransaction {
        try {
            val token = RefreshTokenDAO.findById(UUID.fromString(tokenId))
            if (token != null) {
                token.isRevoked = true
                token.revokedAt = Clock.System.now()
                true
            } else {
                false
            }
        } catch (ex: Exception) {
            log.error("Failed to revoke token: $tokenId", ex)
            false
        }
    }

    override suspend fun revokeAllUserTokens(userId: UUID): Int = suspendTransaction {
        try {
            val now = Clock.System.now()
            val tokens = RefreshTokenDAO.find {
                (RefreshTokenTable.userId eq userId) and
                        (RefreshTokenTable.isRevoked eq false)
            }
            var count = 0
            tokens.forEach { token ->
                token.isRevoked = true
                token.revokedAt = now
                count++
            }
            count
        } catch (ex: Exception) {
            log.error("Failed to revoke all tokens for user: $userId", ex)
            0
        }
    }

    override suspend fun deleteExpiredTokens(): Int = suspendTransaction {
        try {
            val now = Clock.System.now()
            RefreshTokenTable.deleteWhere {
                RefreshTokenTable.expiresAt less now
            }
        } catch (ex: Exception) {
            log.error("Failed to delete expired tokens", ex)
            0
        }
    }
}
