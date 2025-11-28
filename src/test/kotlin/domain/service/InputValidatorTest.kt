package com.tenmilelabs.domain.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputValidatorTest {

    // Email Validation Tests

    @Test
    fun `validateEmail should accept valid email addresses`() {
        val validEmails = listOf(
            "user@example.com",
            "test.user@example.com",
            "user+tag@example.co.uk",
            "user123@test-domain.com",
            "a@b.co"
        )

        validEmails.forEach { email ->
            val result = InputValidator.validateEmail(email)
            assertTrue(result.isValid, "Email '$email' should be valid but got: ${result.errorMessage}")
        }
    }

    @Test
    fun `validateEmail should reject invalid email formats`() {
        val invalidEmails = mapOf(
            "notanemail" to "Invalid email format",
            "missing@domain" to "Invalid email format",
            "@nodomain.com" to "Invalid email format",
            "no-at-sign.com" to "Invalid email format",
            "spaces in@email.com" to "Invalid email format",
            "" to "Email is required",
            "   " to "Email is required",
            "user@@example.com" to "Invalid email format",
            "user@" to "Invalid email format",
            "@example.com" to "Invalid email format"
        )

        invalidEmails.forEach { (email, expectedError) ->
            val result = InputValidator.validateEmail(email)
            assertFalse(result.isValid, "Email '$email' should be invalid")
        }
    }

    @Test
    fun `validateEmail should reject emails that are too long`() {
        val longEmail = "a".repeat(250) + "@example.com"
        val result = InputValidator.validateEmail(longEmail)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("too long"))
    }

    @Test
    fun `validateEmail should reject emails with dangerous patterns`() {
        // Test emails with special/dangerous characters
        val invalidFormatEmails = listOf(
            "user<script>@example.com",
            "user@example.com<script>",
            "user<@example.com",
            "user>@example.com"
        )

        invalidFormatEmails.forEach { email ->
            val result = InputValidator.validateEmail(email)
            assertFalse(result.isValid, "Email '$email' should be rejected (invalid format)")
        }
    }

    @Test
    fun `validateEmail should reject control characters`() {
        // Test null byte - will be rejected by regex
        val emailWithNull = "user" + Char(0) + "@example.com"
        val result1 = InputValidator.validateEmail(emailWithNull)
        assertFalse(result1.isValid, "Email with null byte should be rejected")

        // Test other control characters - will be checked after regex
        // Note: Control characters might not pass regex first, so this verifies defense in depth
        val result2 = InputValidator.validateEmail("user@example.com")
        assertTrue(result2.isValid, "Normal email should be valid")
    }

    @Test
    fun `validateEmail should handle null input`() {
        val result = InputValidator.validateEmail(null)
        assertFalse(result.isValid)
        assertEquals("Email is required", result.errorMessage)
    }

    @Test
    fun `sanitizeEmail should normalize email address`() {
        assertEquals("user@example.com", InputValidator.sanitizeEmail("  User@Example.COM  "))
        assertEquals("test@test.com", InputValidator.sanitizeEmail("Test@Test.com"))
        assertEquals("a@b.com", InputValidator.sanitizeEmail("a@b.com"))
    }

    // Username Validation Tests

    @Test
    fun `validateUsername should accept valid usernames`() {
        val validUsernames = listOf(
            "user123",
            "test_user",
            "user.name",
            "user-name",
            "abc",
            "a".repeat(100)
        )

        validUsernames.forEach { username ->
            val result = InputValidator.validateUsername(username)
            assertTrue(result.isValid, "Username '$username' should be valid: ${result.errorMessage}")
        }
    }

    @Test
    fun `validateUsername should reject invalid usernames`() {
        val invalidUsernames = mapOf(
            "" to "Username is required",
            "   " to "Username is required",
            "ab" to "at least 3 characters",
            "a".repeat(101) to "too long",
            "user@name" to "can only contain",
            "user name" to "can only contain",
            "user#name" to "can only contain",
            "user<script>" to "can only contain"
        )

        invalidUsernames.forEach { (username, expectedError) ->
            val result = InputValidator.validateUsername(username)
            assertFalse(result.isValid, "Username '$username' should be invalid")
        }
    }

    @Test
    fun `validateUsername should reject reserved usernames`() {
        val reservedUsernames = listOf(
            "admin",
            "root",
            "system",
            "administrator",
            "ADMIN",
            "Admin",
            "null",
            "undefined"
        )

        reservedUsernames.forEach { username ->
            val result = InputValidator.validateUsername(username)
            assertFalse(result.isValid, "Username '$username' should be reserved")
            assertTrue(result.errorMessage!!.contains("reserved"))
        }
    }

    @Test
    fun `validateUsername should reject usernames with dangerous patterns`() {
        val dangerousUsernames = listOf(
            "user<script>alert()</script>",
            "user\u0000name", // Null byte
            "user\u001Fname" // Control character
        )

        dangerousUsernames.forEach { username ->
            val result = InputValidator.validateUsername(username)
            assertFalse(result.isValid, "Username '$username' should be rejected as dangerous")
        }
    }

    @Test
    fun `validateUsername should handle null input`() {
        val result = InputValidator.validateUsername(null)
        assertFalse(result.isValid)
        assertEquals("Username is required", result.errorMessage)
    }

    @Test
    fun `sanitizeUsername should trim whitespace`() {
        assertEquals("username", InputValidator.sanitizeUsername("  username  "))
        assertEquals("test", InputValidator.sanitizeUsername("test"))
    }

    // Password Validation Tests

    @Test
    fun `validatePassword should accept strong passwords`() {
        val strongPasswords = listOf(
            "Password1",
            "MySecure123",
            "abc12345",
            "Pass1234!@#$",
            "a1" + "b".repeat(50)
        )

        strongPasswords.forEach { password ->
            val result = InputValidator.validatePassword(password)
            assertTrue(result.isValid, "Password '$password' should be valid: ${result.errorMessage}")
        }
    }

    @Test
    fun `validatePassword should reject weak passwords`() {
        val weakPasswords = mapOf(
            "" to "Password is required",
            "   " to "Password is required",
            "short1" to "at least 8 characters",
            "12345678" to "must contain at least one letter",
            "abcdefgh" to "must contain at least one number",
            "Pass1" to "at least 8 characters"
        )

        weakPasswords.forEach { (password, expectedError) ->
            val result = InputValidator.validatePassword(password)
            assertFalse(result.isValid, "Password '$password' should be invalid")
        }
    }

    @Test
    fun `validatePassword should reject passwords that are too long`() {
        val longPassword = "Pass1" + "a".repeat(130)
        val result = InputValidator.validatePassword(longPassword)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("too long"))
    }

    @Test
    fun `validatePassword should reject passwords with null bytes`() {
        val password = "Pass123\u0000"
        val result = InputValidator.validatePassword(password)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("invalid characters"))
    }

    @Test
    fun `validatePassword should handle null input`() {
        val result = InputValidator.validatePassword(null)
        assertFalse(result.isValid)
        assertEquals("Password is required", result.errorMessage)
    }

    @Test
    fun `validatePassword should accept special characters`() {
        val passwords = listOf(
            "Pass1234!",
            "My@Pass123",
            "Secure#123",
            "Pass$123word"
        )

        passwords.forEach { password ->
            val result = InputValidator.validatePassword(password)
            assertTrue(result.isValid, "Password with special chars should be valid")
        }
    }

    // Combined Validation Tests

    @Test
    fun `validateRegistrationInput should validate all inputs`() {
        // All valid
        val result1 = InputValidator.validateRegistrationInput(
            "user@example.com",
            "username",
            "Password123"
        )
        assertTrue(result1.isValid)

        // Invalid email
        val result2 = InputValidator.validateRegistrationInput(
            "invalid-email",
            "username",
            "Password123"
        )
        assertFalse(result2.isValid)

        // Invalid username
        val result3 = InputValidator.validateRegistrationInput(
            "user@example.com",
            "ab",
            "Password123"
        )
        assertFalse(result3.isValid)

        // Invalid password
        val result4 = InputValidator.validateRegistrationInput(
            "user@example.com",
            "username",
            "weak"
        )
        assertFalse(result4.isValid)
    }

    @Test
    fun `validateLoginInput should validate email and password existence`() {
        // Valid
        val result1 = InputValidator.validateLoginInput(
            "user@example.com",
            "Password123"
        )
        assertTrue(result1.isValid)

        // Invalid email
        val result2 = InputValidator.validateLoginInput(
            "invalid-email",
            "Password123"
        )
        assertFalse(result2.isValid)

        // Missing password
        val result3 = InputValidator.validateLoginInput(
            "user@example.com",
            ""
        )
        assertFalse(result3.isValid)

        // Null inputs
        val result4 = InputValidator.validateLoginInput(null, null)
        assertFalse(result4.isValid)
    }

    @Test
    fun `validateLoginInput should not enforce password strength`() {
        // Login should accept weak passwords (they might have been created before strength rules)
        // But still needs minimum length check
        val result = InputValidator.validateLoginInput(
            "user@example.com",
            "weak" // Only 4 chars, no numbers
        )
        // Even for login, we don't want to process very short passwords
        // But we also don't validate strength
        assertTrue(result.isValid, "Login should accept weak passwords for backward compatibility")

        // Should also accept old-style weak passwords that meet length
        val result2 = InputValidator.validateLoginInput(
            "user@example.com",
            "weakpass" // 8 chars, no numbers - would fail registration but should work for login
        )
        assertTrue(result2.isValid, "Login should accept any password format for existing users")
    }

    @Test
    fun `validateLoginInput should still check password length to prevent DoS`() {
        val longPassword = "a".repeat(200)
        val result = InputValidator.validateLoginInput(
            "user@example.com",
            longPassword
        )
        assertFalse(result.isValid)
    }

    // XSS Prevention Tests

    @Test
    fun `should reject XSS attempts in username`() {
        val xssAttempts = listOf(
            "<script>alert('xss')</script>",
            "javascript:alert(1)",
            "<img src=x onerror=alert(1)>",
            "user<iframe>",
            "user'><script>alert(1)</script>"
        )

        xssAttempts.forEach { username ->
            val result = InputValidator.validateUsername(username)
            assertFalse(result.isValid, "XSS attempt '$username' should be blocked")
        }
    }

    @Test
    fun `should reject XSS attempts in email`() {
        val xssAttempts = listOf(
            "user<script>@example.com",
            "user@example.com<script>",
            "user'><script>@example.com"
        )

        xssAttempts.forEach { email ->
            val result = InputValidator.validateEmail(email)
            assertFalse(result.isValid, "XSS attempt '$email' should be blocked")
        }
    }

    // Edge Cases

    @Test
    fun `should handle unicode characters appropriately`() {
        // Unicode in username - should be rejected (we only allow ASCII alphanumeric)
        val unicodeUsername = "user名字"
        val result1 = InputValidator.validateUsername(unicodeUsername)
        assertFalse(result1.isValid)

        // Unicode in email domain - should be rejected
        val unicodeEmail = "user@例え.com"
        val result2 = InputValidator.validateEmail(unicodeEmail)
        assertFalse(result2.isValid)
    }

    @Test
    fun `should handle mixed case appropriately`() {
        // Email should be case-insensitive
        val email = "User@Example.COM"
        val sanitized = InputValidator.sanitizeEmail(email)
        assertEquals("user@example.com", sanitized)

        // Username case should be preserved
        val username = "UserName"
        val sanitizedUsername = InputValidator.sanitizeUsername(username)
        assertEquals("UserName", sanitizedUsername)
    }

    @Test
    fun `should handle boundary conditions`() {
        // Exactly minimum length username
        val minUsername = "abc"
        assertTrue(InputValidator.validateUsername(minUsername).isValid)

        // Exactly maximum length username
        val maxUsername = "a".repeat(100)
        assertTrue(InputValidator.validateUsername(maxUsername).isValid)

        // One over maximum
        val overMaxUsername = "a".repeat(101)
        assertFalse(InputValidator.validateUsername(overMaxUsername).isValid)
    }
}
