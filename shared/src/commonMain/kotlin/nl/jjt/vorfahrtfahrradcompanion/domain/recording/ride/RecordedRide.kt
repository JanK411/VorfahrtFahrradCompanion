package nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride

import kotlin.time.Instant

/**
 * A ride as it is looked back on, rather than the one being recorded right now — that is [Ride].
 * What the rider is shown in the list of everything they have surveyed, and what a send is aimed at.
 *
 * [segments] is how many observations hang off it, which is the only measure of a ride there is: no
 * distance is recorded, because no GPS track is kept.
 */
data class RecordedRide(
    val id: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val name: String?,
    val segments: Int,
    val uploadedAt: Instant?,
) {
    /**
     * Derived rather than stored, so the two columns it reads can never disagree with it. A ride that
     * has not ended is [RideState.OPEN] whatever else is true of it — there is nothing to send while
     * it is still being ridden.
     */
    val state: RideState
        get() = when {
            endedAt == null -> RideState.OPEN
            uploadedAt == null -> RideState.FINISHED
            else -> RideState.UPLOADED
        }
}

/** Where a ride stands: still being ridden, waiting to be sent, or already on the server. */
enum class RideState { OPEN, FINISHED, UPLOADED }
