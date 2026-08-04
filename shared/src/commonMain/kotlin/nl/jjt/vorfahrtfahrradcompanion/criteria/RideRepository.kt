package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.jjt.vorfahrtfahrradcompanion.db.observation.ObservationDao
import nl.jjt.vorfahrtfahrradcompanion.db.ride.RideDao
import nl.jjt.vorfahrtfahrradcompanion.db.ride.RideEntity
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
 * The ride lives here rather than in the ViewModel for the same reason the draft does: a ViewModel does
 * not survive a bottom-bar tab switch. Like the draft, it is memory only and does not outlive the
 * process.
 *
 * TODO: pick a ride back up that the app was killed in the middle of. Its row is written when the ride
 *  opens and so is still there, sitting open — but nothing goes looking for it yet.
 */
class RideRepository(
    private val dao: RideDao,
    private val observations: ObservationDao,
    private val clock: Clock = Clock.System,
) {
    private val _ride = MutableStateFlow<Ride>(Ride.Idle)
    val ride: StateFlow<Ride> = _ride.asStateFlow()

    /** The clock rides are stamped with, for a caller holding on to the moment a button was pressed. */
    val now: Instant get() = clock.now()

    /** The ride a segment ending now would belong to, or null while none is running. */
    val openId: Long? get() = (_ride.value as? Ride.Open)?.id

    /**
     * Opens a ride. Ignored while one is already running.
     *
     * The row goes in here rather than at the end because the segments recorded along the way point at
     * it — there is no storing one before its ride exists.
     */
    suspend fun start() {
        if (_ride.value is Ride.Open) return
        val startedAt = clock.now()
        _ride.value = Ride.Open(dao.insert(RideEntity(startedAtEpochMs = startedAt.toEpochMilliseconds())), startedAt)
    }

    /** What the open ride amounts to if it ends at [endedAt], or null while no ride is running. */
    suspend fun summary(endedAt: Instant): RideSummary? {
        val open = _ride.value as? Ride.Open ?: return null
        return RideSummary(open.startedAt, endedAt, observations.countForRide(open.id))
    }

    /**
     * Closes the open ride at [endedAt] under [name], blank being no name at all.
     *
     * A ride holding no segments is deleted rather than closed: it describes nothing, the same reason an
     * empty draft is never stored. Does nothing while no ride is running.
     */
    suspend fun end(endedAt: Instant, name: String?) {
        val open = _ride.value as? Ride.Open ?: return
        if (observations.countForRide(open.id) == 0) {
            dao.delete(open.id)
        } else {
            dao.close(open.id, endedAt.toEpochMilliseconds(), name?.trim()?.ifBlank { null })
        }
        _ride.value = Ride.Idle
    }
}
