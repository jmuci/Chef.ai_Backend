package com.tenmilelabs.infrastructure.database.repositoryImpl

import com.tenmilelabs.domain.model.RefreshToken
import com.tenmilelabs.infrastructure.database.dao.RefreshTokenDAO
import com.tenmilelabs.infrastructure.database.mappers.daoToRefreshToken
import com.tenmilelabs.infrastructure.database.mappers.suspendTransaction
import com.tenmilelabs.infrastructure.database.tables.RefreshTokenTable
import io.ktor.util.logging.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.update
import java.util.*

interface RefreshTokenRepository {
    suspend fun createRefreshToken(userId: UUID, tokenHash: String, expiresAt: Instant): RefreshToken?
    suspend fun findByTokenHash(tokenHash: String): RefreshToken?
    /**
     * Revokes [tokenId] if, and only if, it is not already revoked — a single conditional
     * `UPDATE ... WHERE is_revoked = false`. Returns true only for the one call that actually made
     * the transition, which is what lets refresh-token rotation treat a `false` as reuse: of two
     * concurrent refreshes presenting the same token, exactly one wins.
     */
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
            val updated = RefreshTokenTable.update({
                (RefreshTokenTable.id eq UUID.fromString(tokenId)) and (RefreshTokenTable.isRevoked eq false)
            }) {
                it[isRevoked] = true
                it[revokedAt] = Clock.System.now()
            }
            updated == 1
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
