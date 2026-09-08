package application.service

import com.tenmilelabs.application.service.DEVELOPMENT_JWT_SECRET
import com.tenmilelabs.application.service.MIN_JWT_SECRET_LENGTH
import com.tenmilelabs.application.service.PUBLISHED_JWT_SECRETS
import com.tenmilelabs.application.service.resolveJwtSecret
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression cover for security audit F2: the signing secret used to fall back to the literal
 * `"secret"`, and `application.yaml` shipped a placeholder, so every deployment signed tokens with
 * a string published in this repository.
 *
 * These pin the rules of [resolveJwtSecret] directly — no application boot required, which is the
 * point of keeping it a pure function.
 */
class JwtSecretResolutionTest {

    private val strongSecret = "n1JQ0PZ8kx7dW2vTgYrLmCeAoBu4sHhF6qKpXyZi3RtN"

    @Test
    fun `accepts a sufficiently long configured secret`() {
        assertEquals(strongSecret, resolveJwtSecret(strongSecret, developmentMode = false))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals(strongSecret, resolveJwtSecret("  $strongSecret\n", developmentMode = false))
    }

    @Test
    fun `refuses to start in production when no secret is configured`() {
        listOf(null, "", "   ").forEach { value ->
            val failure = assertFailsWith<IllegalStateException> {
                resolveJwtSecret(value, developmentMode = false)
            }
            assertTrue(
                failure.message.orEmpty().contains("JWT_SECRET"),
                "the error must name the variable to set, was: ${failure.message}"
            )
        }
    }

    @Test
    fun `refuses to start in production on a secret published in this repository`() {
        PUBLISHED_JWT_SECRETS.forEach { published ->
            assertFailsWith<IllegalStateException>("'$published' must be rejected") {
                resolveJwtSecret(published, developmentMode = false)
            }
        }
    }

    @Test
    fun `the placeholder that used to ship in application yaml is covered`() {
        // Named explicitly: this is the exact value the repository shipped, and the one that has
        // to keep failing even if PUBLISHED_JWT_SECRETS is edited later.
        assertTrue("your-secret-key-change-this-in-production" in PUBLISHED_JWT_SECRETS)
        assertTrue("secret" in PUBLISHED_JWT_SECRETS)
    }

    @Test
    fun `refuses to start in production on a short secret`() {
        val short = "a".repeat(MIN_JWT_SECRET_LENGTH - 1)
        assertFailsWith<IllegalStateException> { resolveJwtSecret(short, developmentMode = false) }
        // Exactly at the boundary is fine.
        val atBoundary = "b".repeat(MIN_JWT_SECRET_LENGTH)
        assertEquals(atBoundary, resolveJwtSecret(atBoundary, developmentMode = false))
    }

    @Test
    fun `falls back to the development key only in development mode`() {
        assertEquals(DEVELOPMENT_JWT_SECRET, resolveJwtSecret(null, developmentMode = true))
        assertEquals(DEVELOPMENT_JWT_SECRET, resolveJwtSecret("secret", developmentMode = true))
    }

    @Test
    fun `a real configured secret wins over the development fallback`() {
        assertEquals(strongSecret, resolveJwtSecret(strongSecret, developmentMode = true))
    }

    @Test
    fun `the development key is not itself one of the published secrets`() {
        assertTrue(DEVELOPMENT_JWT_SECRET !in PUBLISHED_JWT_SECRETS)
        PUBLISHED_JWT_SECRETS.forEach { assertNotEquals(it, DEVELOPMENT_JWT_SECRET) }
    }
}
