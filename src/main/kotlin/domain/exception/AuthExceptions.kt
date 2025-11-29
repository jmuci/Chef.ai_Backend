package com.tenmilelabs.domain.exception

/**
 * Base exception for all authentication-related errors
 */
sealed class AuthException(message: String) : Exception(message)

/**
 * Thrown when input validation fails
 */
class ValidationException(message: String) : AuthException(message)

/**
 * Thrown when user already exists (e.g., duplicate email)
 */
class UserAlreadyExistsException(message: String) : AuthException(message)

/**
 * Thrown when credentials are invalid
 */
class InvalidCredentialsException(message: String) : AuthException(message)

/**
 * Thrown when user is not found
 */
class UserNotFoundException(message: String) : AuthException(message)

/**
 * Thrown when refresh token is invalid, expired, or revoked
 */
class InvalidRefreshTokenException(message: String) : AuthException(message)

/**
 * Thrown when token reuse is detected (security breach)
 */
class TokenReuseDetectedException(message: String) : AuthException(message)

/**
 * Thrown when internal operations fail (e.g., database errors)
 */
class AuthInternalException(message: String, cause: Throwable? = null) : AuthException(message) {
    init {
        if (cause != null) {
            initCause(cause)
        }
    }
}
