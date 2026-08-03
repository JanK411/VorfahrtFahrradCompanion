package nl.jjt.vorfahrtfahrradcompanion.location

import kotlinx.coroutines.flow.Flow

/**
 * Streams device positions. Updates start when the flow is collected and stop when collection ends.
 * The flow fails if the location permission is missing.
 */
interface LocationProvider {
    fun locations(intervalMillis: Long = 1_000): Flow<Location>

    /**
     * The position the platform already holds, without asking the hardware for a new one — null
     * where there is none, or where the permission for it is missing. For the things that only need
     * to know roughly where the device is, such as when the sun sets there.
     */
    suspend fun lastKnown(): Location?
}
