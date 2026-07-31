package nl.jjt.vorfahrtfahrradcompanion.daylight

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import nl.jjt.vorfahrtfahrradcompanion.location.Location
import nl.jjt.vorfahrtfahrradcompanion.location.LocationProvider
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** How often the sun is asked about. Sunset takes minutes to happen; the screen can wait one. */
private val Tick = 1.minutes

/**
 * Whether it is dark out, which is the only thing this app dims its screen for. The device's own
 * light/dark setting says nothing about that: a phone left on dark all year would put the app in
 * night colours in the middle of a summer afternoon, where they are the hardest to read.
 *
 * Where the rider is comes from the position the platform already has to hand — no fix is requested
 * for this, and nothing is turned on for it. Without one, the answer is "day": an app that cannot
 * tell must not be the dark one in daylight.
 */
class Daylight(
    private val provider: LocationProvider,
    private val clock: Clock = Clock.System,
) {
    /** The last position seen, so a fix that goes away does not take the sunset with it. */
    private var lastKnown: Location? = null

    val isNight: Flow<Boolean> = flow {
        while (true) {
            emit(night())
            delay(Tick)
        }
    }.distinctUntilChanged()

    private suspend fun night(): Boolean {
        val where = provider.lastKnown()?.also { lastKnown = it } ?: lastKnown ?: return false
        return isNightAt(clock.now(), where.latitude, where.longitude)
    }
}
