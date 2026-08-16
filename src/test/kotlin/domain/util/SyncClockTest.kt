package domain.util

import com.tenmilelabs.domain.util.millisecondPrecisionNow
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * `Clock.System.now()` resolves to `java.time.Instant.now()` on the JVM, which carries
 * microsecond precision — see [millisecondPrecisionNow]'s doc for why persisting that
 * untruncated into a `server_updated_at` column freezes `/sync/pull` cursors.
 */
class SyncClockTest {
    @Test
    fun millisecondPrecisionNowHasNoSubMillisecondRemainder() {
        // Sampled repeatedly because a single call landing exactly on a millisecond boundary
        // wouldn't catch a truncation bug — java.time.Instant.now() on this JVM reliably
        // returns microsecond-precision instants (see the class doc).
        repeat(20) {
            val now = millisecondPrecisionNow()
            val subMillisecondNanos = now.nanosecondsOfSecond % 1_000_000
            assertEquals(0, subMillisecondNanos, "expected no sub-millisecond precision, got $now")
        }
    }
}
