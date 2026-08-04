package nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride

import kotlin.time.Instant

/** What the rider is shown on their way out of a ride, so they can see what they are about to save. */
data class RideSummary(val startedAt: Instant, val endedAt: Instant, val segments: Int)
