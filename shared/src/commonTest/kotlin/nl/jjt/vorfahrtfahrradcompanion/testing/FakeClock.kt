package nl.jjt.vorfahrtfahrradcompanion.testing

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A clock a test drives itself. Advanceable rather than fixed, because most of what wants one here
 * cares about the distance between two moments — a segment's start and end, a cache going stale.
 */
class FakeClock(var instant: Instant) : Clock {
    override fun now() = instant

    operator fun plusAssign(duration: Duration) {
        instant += duration
    }
}
