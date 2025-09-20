package com.tenmilelabs.domain.service

/**
 * Input validation and sanitization utilities for authentication
 */
object InputValidator {

    // Length constraints
    private const val MAX_EMAIL_LENGTH = 254 // RFC 5321
    private const val MAX_USERNAME_LENGTH = 100
    private const val MIN_USERNAME_LENGTH = 3
    private const val MAX_PASSWORD_LENGTH = 128 // BCrypt max
    private const val MIN_PASSWORD_LENGTH = 8

    // Validation patterns
    private val EMAIL_REGEX = """^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$""".toRegex()
    private val USERNAME_REGEX = """^[a-zA-Z0-9_.\-]+$""".toRegex() // Alphanumeric + safe chars only

    // Dangerous patterns to reject
    private val DANGEROUS_PATTERNS = listOf(
        """<script""".toRegex(RegexOption.IGNORE_CASE),
        """javascript:""".toRegex(RegexOption.IGNORE_CASE),
        """on\w+\s*=""".toRegex(RegexOption.IGNORE_CASE), // onclick=, onerror=, etc.
        """\u0000""".toRegex() // Null bytes
    )

    // Control characters to reject (using proper Unicode escapes)
    private fun containsControlCharacters(input: String): Boolean {
        return input.any { it.code in 0..31 || it.code == 127 }
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    /**
     * Validate and sanitize email input
     */
    fun validateEmail(email: String?): ValidationResult {
        if (email.isNullOrBlank()) {
            return ValidationResult(false, "Email is required")
        }

        // Trim whitespace
        val trimmedEmail = email.trim()

        // Length check
        if (trimmedEmail.length > MAX_EMAIL_LENGTH) {
            return ValidationResult(false, "Email is too long (max $MAX_EMAIL_LENGTH characters)")
        }

        // Format check
        if (!EMAIL_REGEX.matches(trimmedEmail)) {
            return ValidationResult(false, "Invalid email format")
        }

        // Check for dangerous patterns
        if (containsDangerousPatterns(trimmedEmail) || containsControlCharacters(trimmedEmail)) {
            return ValidationResult(false, "Email contains invalid characters")
        }

        // Additional checks for email bombs
        if (trimmedEmail.count { it == '@' } > 1) {
            return ValidationResult(false, "Invalid email format")
        }

        return ValidationResult(true)
    }

    /**
     * Validate and sanitize username input
     */
    fun validateUsername(username: String?): ValidationResult {
        if (username.isNullOrBlank()) {
            return ValidationResult(false, "Username is required")
        }

        val trimmedUsername = username.trim()

        // Length check
        if (trimmedUsername.length < MIN_USERNAME_LENGTH) {
            return ValidationResult(false, "Username must be at least $MIN_USERNAME_LENGTH characters")
        }

        if (trimmedUsername.length > MAX_USERNAME_LENGTH) {
            return ValidationResult(false, "Username is too long (max $MAX_USERNAME_LENGTH characters)")
        }

        // Format check - only allow alphanumeric and safe special characters
        if (!USERNAME_REGEX.matches(trimmedUsername)) {
            return ValidationResult(false, "Username can only contain letters, numbers, dots, hyphens, and underscores")
        }

        // Check for dangerous patterns
        if (containsDangerousPatterns(trimmedUsername) || containsControlCharacters(trimmedUsername)) {
            return ValidationResult(false, "Username contains invalid characters")
        }

        // Don't allow usernames that look like system accounts
        val lowerUsername = trimmedUsername.lowercase()
        if (lowerUsername in listOf("admin", "root", "system", "administrator", "null", "undefined")) {
            return ValidationResult(false, "This username is reserved")
        }

        return ValidationResult(true)
    }

    /**
     * Validate password input
     */
    fun validatePassword(password: String?): ValidationResult {
        if (password.isNullOrBlank()) {
            return ValidationResult(false, "Password is required")
        }

        // Length check
        if (password.length < MIN_PASSWORD_LENGTH) {
            return ValidationResult(false, "Password must be at least $MIN_PASSWORD_LENGTH characters")
        }

        if (password.length > MAX_PASSWORD_LENGTH) {
            return ValidationResult(false, "Password is too long (max $MAX_PASSWORD_LENGTH characters)")
        }

        // Strength check - require letter and number
        if (!password.any { it.isLetter() }) {
            return ValidationResult(false, "Password must contain at least one letter")
        }

        if (!password.any { it.isDigit() }) {
            return ValidationResult(false, "Password must contain at least one number")
        }

        // Check for null bytes and other dangerous patterns
        if (password.contains('\u0000')) {
            return ValidationResult(false, "Password contains invalid characters")
        }

        return ValidationResult(true)
    }

    /**
     * Sanitize email for safe storage and use
     */
    fun sanitizeEmail(email: String): String {
        return email.trim().lowercase()
    }

    /**
     * Sanitize username for safe storage and use
     */
    fun sanitizeUsername(username: String): String {
        return username.trim()
    }

    /**
     * Check if input contains dangerous patterns
     */
    private fun containsDangerousPatterns(input: String): Boolean {
        return DANGEROUS_PATTERNS.any { it.containsMatchIn(input) }
    }

    /**
     * Validate all registration inputs
     */
    fun validateRegistrationInput(
        email: String?,
        username: String?,
        password: String?
    ): ValidationResult {
        validateEmail(email).let { if (!it.isValid) return it }
        validateUsername(username).let { if (!it.isValid) return it }
        validatePassword(password).let { if (!it.isValid) return it }
        return ValidationResult(true)
    }

    /**
     * Validate all login inputs
     */
    fun validateLoginInput(
        email: String?,
        password: String?
    ): ValidationResult {
        validateEmail(email).let { if (!it.isValid) return it }

        // For login, we don't validate password strength, just that it exists
        if (password.isNullOrBlank()) {
            return ValidationResult(false, "Password is required")
        }

        if (password.length > MAX_PASSWORD_LENGTH) {
            return ValidationResult(false, "Invalid credentials")
        }

        return ValidationResult(true)
    }
}
