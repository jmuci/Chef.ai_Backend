package com.tenmilelabs.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain model for refresh token
 */
data class RefreshToken(
    val id: String,
    val userId: String,
    val tokenHash: String,
    val expiresAt: String,
    val createdAt: String,
    val isRevoked: Boolean = false,
    val revokedAt: String? = null
)

/**
 * Metadata about a refresh token for responses
 */
@Serializable
data class RefreshTokenInfo(
    val expiresAt: String,
    val createdAt: String
)
