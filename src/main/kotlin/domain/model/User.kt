package com.tenmilelabs.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val email: String,
    val username: String,
    val createdAt: String
)

// Internal representation with password hash (not serialized/exposed)
data class UserWithPassword(
    val id: String,
    val email: String,
    val username: String,
    val passwordHash: String,
    val createdAt: String
)
