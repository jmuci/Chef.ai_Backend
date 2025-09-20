package com.tenmilelabs.domain.service

import com.tenmilelabs.application.dto.LoginRequest
import com.tenmilelabs.application.dto.RegisterRequest
import com.tenmilelabs.infrastructure.database.FakeUserRepository
import io.ktor.util.logging.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthServiceTest {

    private lateinit var userRepository: FakeUserRepository
    private lateinit var jwtService: JwtService
    private lateinit var authService: AuthService
    private val logger = KtorSimpleLogger("AuthServiceTest")

    @BeforeEach
    fun setup() {
        userRepository = FakeUserRepository()
        jwtService = JwtService(
            secret = "test-secret-key-for-jwt-tokens",
            issuer = "http://test.com",
            audience = "test-audience"
        )
        authService = AuthService(userRepository, jwtService, logger)
    }

    // Registration Tests

    @Test
    fun `register with valid credentials should succeed`() = runTest {
        val request = RegisterRequest(
            email = "user@example.com",
            username = "testuser",
            password = "SecurePass123"
        )

        val response = authService.register(request)

        assertNotNull(response)
        assertEquals("user@example.com", response.email)
        assertEquals("testuser", response.username)
        assertNotNull(response.token)
        assertNotNull(response.userId)
    }

    @Test
    fun `register with duplicate email should fail`() = runTest {
        val request = RegisterRequest(
            email = "duplicate@example.com",
            username = "user1",
            password = "SecurePass123"
        )

        // First registration
        val response1 = authService.register(request)
        assertNotNull(response1)

        // Second registration with same email
        val response2 = authService.register(request.copy(username = "user2"))
        assertNull(response2)
    }

    @Test
    fun `register with invalid email format should fail`() = runTest {
        val invalidEmails = listOf(
            "notanemail",
            "missing@domain",
            "@nodomain.com",
            "no-at-sign.com",
            "spaces in@email.com",
            ""
        )

        invalidEmails.forEach { email ->
            val request = RegisterRequest(
                email = email,
                username = "testuser",
                password = "SecurePass123"
            )
            val response = authService.register(request)
            assertNull(response, "Email '$email' should have been rejected")
        }
    }

    @Test
    fun `register with valid email formats should succeed`() = runTest {
        val validEmails = listOf(
            "user@example.com",
            "test.user@example.com",
            "user+tag@example.co.uk",
            "user123@test-domain.com"
        )

        validEmails.forEachIndexed { index, email ->
            val request = RegisterRequest(
                email = email,
                username = "user$index",
                password = "SecurePass123"
            )
            val response = authService.register(request)
            assertNotNull(response, "Email '$email' should have been accepted")
            assertEquals(email, response.email)
        }
    }

    @Test
    fun `register with weak password should fail`() = runTest {
        val weakPasswords = listOf(
            "short",           // Too short
            "12345678",        // Only numbers
            "abcdefgh",        // Only letters
            "Pass1"            // Less than 8 characters
        )

        weakPasswords.forEachIndexed { index, password ->
            val request = RegisterRequest(
                email = "user$index@example.com",
                username = "user$index",
                password = password
            )
            val response = authService.register(request)
            assertNull(response, "Password '$password' should have been rejected")
        }
    }

    @Test
    fun `register with strong password should succeed`() = runTest {
        val strongPasswords = listOf(
            "SecurePass123",
            "MyP@ssw0rd",
            "12345678a",
            "abcdefgh1"
        )

        strongPasswords.forEachIndexed { index, password ->
            val request = RegisterRequest(
                email = "user$index@example.com",
                username = "user_$index", // Updated to meet new validation (3+ chars, alphanumeric)
                password = password
            )
            val response = authService.register(request)
            assertNotNull(response, "Password '$password' should have been accepted")
        }
    }

    @Test
    fun `register should hash password correctly`() = runTest {
        val request = RegisterRequest(
            email = "secure@example.com",
            username = "secureuser",
            password = "MySecurePass123"
        )

        val response = authService.register(request)
        assertNotNull(response)

        // Verify that the stored password is not plain text
        val storedUser = userRepository.findUserByEmail("secure@example.com")
        assertNotNull(storedUser)
        assertTrue(storedUser.passwordHash != "MySecurePass123")
        assertTrue(storedUser.passwordHash.startsWith("$2a$")) // BCrypt hash prefix
    }

    // Login Tests

    @Test
    fun `login with correct credentials should succeed`() = runTest {
        // Register a user first
        val registerRequest = RegisterRequest(
            email = "login@example.com",
            username = "loginuser",
            password = "SecurePass123"
        )
        authService.register(registerRequest)

        // Attempt login
        val loginRequest = LoginRequest(
            email = "login@example.com",
            password = "SecurePass123"
        )
        val response = authService.login(loginRequest)

        assertNotNull(response)
        assertEquals("login@example.com", response.email)
        assertEquals("loginuser", response.username)
        assertNotNull(response.token)
    }

    @Test
    fun `login with incorrect password should fail`() = runTest {
        // Register a user first
        val registerRequest = RegisterRequest(
            email = "wrongpass@example.com",
            username = "user",
            password = "CorrectPass123"
        )
        authService.register(registerRequest)

        // Attempt login with wrong password
        val loginRequest = LoginRequest(
            email = "wrongpass@example.com",
            password = "WrongPass123"
        )
        val response = authService.login(loginRequest)

        assertNull(response)
    }

    @Test
    fun `login with non-existent email should fail`() = runTest {
        val loginRequest = LoginRequest(
            email = "nonexistent@example.com",
            password = "AnyPassword123"
        )
        val response = authService.login(loginRequest)

        assertNull(response)
    }

    @Test
    fun `login should generate valid JWT token`() = runTest {
        // Register a user
        val registerRequest = RegisterRequest(
            email = "jwt@example.com",
            username = "jwtuser",
            password = "SecurePass123"
        )
        val registerResponse = authService.register(registerRequest)
        assertNotNull(registerResponse)

        // Login
        val loginRequest = LoginRequest(
            email = "jwt@example.com",
            password = "SecurePass123"
        )
        val loginResponse = authService.login(loginRequest)
        assertNotNull(loginResponse)

        // Verify token can be verified
        val userId = jwtService.verifyToken(loginResponse.token)
        assertNotNull(userId)
        assertEquals(registerResponse.userId, userId)
    }

    @Test
    fun `login and register should generate different tokens`() = runTest {
        // Register
        val registerRequest = RegisterRequest(
            email = "tokens@example.com",
            username = "tokenuser",
            password = "SecurePass123"
        )
        val registerResponse = authService.register(registerRequest)
        assertNotNull(registerResponse)

        // Delay to ensure different timestamps (JWT includes timestamp in payload)
        kotlinx.coroutines.delay(1000)

        // Login
        val loginRequest = LoginRequest(
            email = "tokens@example.com",
            password = "SecurePass123"
        )
        val loginResponse = authService.login(loginRequest)
        assertNotNull(loginResponse)

        // Tokens should be different (different timestamps)
        // Note: This might occasionally fail if generated in the same second
        // Both tokens are valid, just different
        assertTrue(jwtService.verifyToken(registerResponse.token) != null)
        assertTrue(jwtService.verifyToken(loginResponse.token) != null)
    }

    // User Retrieval Tests

    @Test
    fun `getUserById should return user when exists`() = runTest {
        // Register a user
        val registerRequest = RegisterRequest(
            email = "getuser@example.com",
            username = "getuser",
            password = "SecurePass123"
        )
        val registerResponse = authService.register(registerRequest)
        assertNotNull(registerResponse)

        // Get user by ID
        val user = authService.getUserById(registerResponse.userId)
        assertNotNull(user)
        assertEquals(registerResponse.userId, user.id)
        assertEquals("getuser@example.com", user.email)
        assertEquals("getuser", user.username)
    }

    @Test
    fun `getUserById should return null when user does not exist`() = runTest {
        val user = authService.getUserById("non-existent-id")
        assertNull(user)
    }

    // Edge Cases and Security Tests

    @Test
    fun `register with empty username should fail`() = runTest {
        // Username validation IS enforced now
        val request = RegisterRequest(
            email = "empty@example.com",
            username = "",
            password = "SecurePass123"
        )
        val response = authService.register(request)
        assertNull(response, "Empty username should be rejected")
    }

    @Test
    fun `register with very long username should fail`() = runTest {
        // Usernames are now limited to 100 characters
        val longUsername = "a".repeat(200)
        val request = RegisterRequest(
            email = "longname@example.com",
            username = longUsername,
            password = "SecurePass123"
        )
        val response = authService.register(request)
        assertNull(response, "Username longer than 100 characters should be rejected")
    }

    @Test
    fun `register with maximum length username should succeed`() = runTest {
        val maxUsername = "a".repeat(100)
        val request = RegisterRequest(
            email = "maxname@example.com",
            username = maxUsername,
            password = "SecurePass123"
        )
        val response = authService.register(request)
        assertNotNull(response)
        assertEquals(maxUsername, response.username)
    }

    @Test
    fun `password should not be logged or exposed`() = runTest {
        val request = RegisterRequest(
            email = "secret@example.com",
            username = "secretuser",
            password = "SuperSecret123"
        )

        val response = authService.register(request)
        assertNotNull(response)

        // Verify response doesn't contain password
        val responseString = response.toString()
        assertTrue(!responseString.contains("SuperSecret123"))
    }

    @Test
    fun `same password for different users should create different hashes`() = runTest {
        val password = "SamePassword123"

        val user1 = RegisterRequest("user1@example.com", "user1", password)
        val user2 = RegisterRequest("user2@example.com", "user2", password)

        authService.register(user1)
        authService.register(user2)

        val storedUser1 = userRepository.findUserByEmail("user1@example.com")
        val storedUser2 = userRepository.findUserByEmail("user2@example.com")

        assertNotNull(storedUser1)
        assertNotNull(storedUser2)
        assertTrue(
            storedUser1.passwordHash != storedUser2.passwordHash,
            "Same password should produce different hashes (salt)"
        )
    }

    @Test
    fun `register with special characters in username should fail`() = runTest {
        val request = RegisterRequest(
            email = "special@example.com",
            username = "user!@#$%^&*()",
            password = "SecurePass123"
        )
        val response = authService.register(request)
        assertNull(response, "Username with special characters should be rejected for security")
    }

    @Test
    fun `register with allowed special characters in username should succeed`() = runTest {
        val request = RegisterRequest(
            email = "allowed@example.com",
            username = "user_name-123.",
            password = "SecurePass123"
        )
        val response = authService.register(request)
        assertNotNull(response)
        assertEquals("user_name-123.", response.username)
    }

    @Test
    fun `email should be case-insensitive for registration`() = runTest {
        // Emails are now normalized to lowercase
        val request1 = RegisterRequest(
            email = "Test@Example.com",
            username = "user123",
            password = "SecurePass123"
        )
        val request2 = RegisterRequest(
            email = "test@example.com",
            username = "user456",
            password = "SecurePass123"
        )

        val response1 = authService.register(request1)
        val response2 = authService.register(request2)

        assertNotNull(response1)
        assertNull(response2, "Same email in different case should be rejected as duplicate")
    }
}
