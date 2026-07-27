package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlin.time.Clock
import kotlin.time.Instant

class FakeClock(val instant: Instant) : Clock {
    override fun now() = instant
}
