package nl.jjt.vorfahrtfahrradcompanion.location

import kotlinx.coroutines.flow.Flow

/**
 * Streams device positions. Updates start when the flow is collected and stop when collection ends.
 * The flow fails if the location permission is missing.
 */
interface LocationProvider {
    fun locations(intervalMillis: Long = 1_000): Flow<Location>
}
