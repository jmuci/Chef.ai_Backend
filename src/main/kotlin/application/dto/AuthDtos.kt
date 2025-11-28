package com.tenmilelabs.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val refreshToken: String,
    val userId: String,
    val username: String,
    val email: String,
    val expiresIn: Long // seconds until access token expires
)

@Serializable
data class ErrorResponse(
    val message: String
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val expiresIn: Long // seconds until access token expires
)
