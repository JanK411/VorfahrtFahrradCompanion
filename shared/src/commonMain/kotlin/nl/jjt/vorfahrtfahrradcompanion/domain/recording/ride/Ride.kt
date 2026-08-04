package nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride

import kotlin.time.Instant

/** The ride being recorded right now, if any. */
sealed interface Ride {
    data object Idle : Ride
    data class Open(val id: Long, val startedAt: Instant) : Ride
}
