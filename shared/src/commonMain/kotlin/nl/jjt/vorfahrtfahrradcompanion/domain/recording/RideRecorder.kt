package nl.jjt.vorfahrtfahrradcompanion.domain.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.Instant

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
class RideRecorder(
    private val store: RideStore,
    private val observations: ObservationStore,
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
        _ride.value = Ride.Open(store.open(startedAt), startedAt)
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
            store.delete(open.id)
        } else {
            store.close(open.id, endedAt, name?.trim()?.ifBlank { null })
        }
        _ride.value = Ride.Idle
    }
}
