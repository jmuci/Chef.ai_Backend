package com.tenmilelabs.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import java.util.UUID

@Serializable
data class User(
    @Contextual
    val id: UUID,
    val email: String,
    val displayName: String,
    val avatarUrl: String,
    val createdAt: String,
    val updatedAt: String
)

// Internal representation with password hash (not serialized/exposed)
data class UserWithPassword(
    val id: UUID,
    val email: String,
    val displayName: String,
    val passwordHash: String,
    val createdAt: String
)
