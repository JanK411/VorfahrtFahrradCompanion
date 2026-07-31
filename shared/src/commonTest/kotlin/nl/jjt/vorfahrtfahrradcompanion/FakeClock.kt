package nl.jjt.vorfahrtfahrradcompanion

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** Advanceable, because a segment's start and end must not land on the same instant. */
class FakeClock(var instant: Instant) : Clock {
    override fun now() = instant

    operator fun plusAssign(duration: Duration) {
        instant += duration
    }
}
