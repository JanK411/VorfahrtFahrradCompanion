package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.ObservationDao
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.RideDao
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.RideEntity
import kotlin.time.Clock
import kotlin.time.Instant

/** The ride being recorded right now, if any. */
sealed interface Ride {
    data object Idle : Ride
    data class Open(val id: Long, val startedAt: Instant) : Ride
}

/** What the rider is shown on their way out of a ride, so they can see what they are about to save. */
data class RideSummary(val startedAt: Instant, val endedAt: Instant, val segments: Int)

/**
 * Owns the ride the segments are being recorded into.
 *
 * Unlike the segment draft, a ride keeps no state of its own: the open row *is* the state. That is what
 * makes a ride outlive the process — the rider who is killed by the task manager halfway through an
 * outing comes back to the same ride — and it needs no resuming to do so.
 */
class RideRepository(
    private val rides: RideDao,
    private val observations: ObservationDao,
    private val clock: Clock = Clock.System,
) {
    val ride: Flow<Ride> = rides.observeOpen().map { it?.toOpen() ?: Ride.Idle }

    /** The clock rides are stamped with, for a caller holding on to the moment a button was pressed. */
    val now: Instant get() = clock.now()

    /** The ride as it stands, for a caller that needs it before it is in a position to collect [ride]. */
    suspend fun current(): Ride = rides.open()?.toOpen() ?: Ride.Idle

    /** Opens a ride. Ignored while one is already running. */
    suspend fun start() {
        if (rides.open() != null) return
        rides.insert(RideEntity(startedAtEpochMs = clock.now().toEpochMilliseconds()))
    }

    /** What the open ride amounts to if it ends at [endedAt], or null while no ride is running. */
    suspend fun summary(endedAt: Instant): RideSummary? {
        val open = rides.open() ?: return null
        return RideSummary(open.toOpen().startedAt, endedAt, observations.countForRide(open.id))
    }

    /**
     * Closes the open ride at [endedAt] under [name], blank being no name at all.
     *
     * A ride holding no segments is deleted rather than closed: it describes nothing, the same reason an
     * empty draft is never stored. Does nothing while no ride is running.
     */
    suspend fun end(endedAt: Instant, name: String?) {
        val open = rides.open() ?: return
        if (observations.countForRide(open.id) == 0) {
            rides.delete(open.id)
        } else {
            rides.close(open.id, endedAt.toEpochMilliseconds(), name?.trim()?.ifBlank { null })
        }
    }

    private fun RideEntity.toOpen() = Ride.Open(id, Instant.fromEpochMilliseconds(startedAtEpochMs))
}
