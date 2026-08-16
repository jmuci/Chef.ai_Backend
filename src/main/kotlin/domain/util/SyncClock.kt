package com.tenmilelabs.domain.util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * `Clock.System.now()` resolves to `java.time.Instant.now()` on the JVM, which carries
 * microsecond precision. Sync cursors (`serverTimestamp`, `since`) are communicated to
 * clients as millisecond `Long`s, so persisting an untruncated instant into a
 * `server_updated_at` column lets a strict `>` comparison re-admit the very row that
 * produced the cursor: the client's millisecond-floored `since` is always slightly less
 * than the row's true stored value. Two calls landing in the same millisecond (a batched
 * push, a bulk import) reproduce this deterministically.
 *
 * Always use this instead of `Clock.System.now()` when the resulting instant will be
 * written to a column that a sync cursor is later derived from or compared against.
 */
fun millisecondPrecisionNow(): Instant = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
