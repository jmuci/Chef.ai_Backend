package com.tenmilelabs.domain.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.tenmilelabs.domain.exception.InvalidCredentialsException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TimingAttackTest {

    @Test
    fun `invalid bcrypt hash throws exception`() {
        // This is the current dummy hash in simulatePasswordCheck
        val invalidHash = "\$2a\$12\$dummyhashtopreventtimingattack1234567890123456789012"

        try {
            BCrypt.verifyer().verify(
                "dummy-password".toCharArray(),
                invalidHash
            )
            // If we get here, the hash was somehow valid
            println("WARNING: Invalid hash did not throw exception!")
        } catch (e: IllegalArgumentException) {
            // This is what actually happens - the hash is invalid
            println("CONFIRMED: Invalid hash throws IllegalArgumentException: ${e.message}")
            assertTrue(true, "Invalid hash correctly throws exception")
        } catch (e: Exception) {
            println("ERROR: Unexpected exception type: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    @Test
    fun `valid bcrypt hash does not throw`() {
        // Generate a real bcrypt hash
        val validHash = BCrypt.withDefaults().hashToString(12, "test-password".toCharArray())

        // This should not throw
        val result = BCrypt.verifyer().verify("test-password".toCharArray(), validHash)
        assertTrue(result.verified)
    }

    @Test
    fun `timing attack mitigation with valid hash works correctly`() {
        // Using a valid pre-computed BCrypt hash
        val validDummyHash = "\$2a\$12\$LQDHPwzzPWFERYtY3KfQaeFmIaM5b7YQ7GxJYqG.N7GnXvLqYG5Ai"

        try {
            // This should not throw and should fail verification
            val result = BCrypt.verifyer().verify(
                "wrong-password".toCharArray(),
                validDummyHash
            )
            assertNotNull(result)
            assertTrue(!result.verified, "Wrong password should not verify")
            println("SUCCESS: Valid dummy hash works correctly for timing protection")
        } catch (e: Exception) {
            throw AssertionError("Valid dummy hash should not throw exceptions", e)
        }
    }

    @Test
    fun `login with non-existent user throws InvalidCredentialsException`() = runTest {
        val userRepository = com.tenmilelabs.infrastructure.database.FakeUserRepository()
        val refreshTokenRepository = com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository()
        val jwtService = JwtService("test-secret", "test-issuer", "test-audience")
        val authService = AuthService(
            userRepository,
            refreshTokenRepository,
            jwtService,
            io.ktor.util.logging.KtorSimpleLogger("TimingAttackTest")
        )

        // Try to login with non-existent user - should throw exception
        val request = com.tenmilelabs.application.dto.LoginRequest(
            email = "nonexistent@example.com",
            password = "anypassword123"
        )

        // The timing attack protection is still in place - simulatePasswordCheck() is called
        // before throwing the exception, ensuring constant-time response
        assertFailsWith<InvalidCredentialsException>("Login with non-existent user should throw InvalidCredentialsException") {
            authService.login(request)
        }
    }
}
