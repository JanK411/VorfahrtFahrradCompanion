package nl.jjt.vorfahrtfahrradcompanion.testing

import kotlinx.coroutines.flow.Flow
import nl.jjt.vorfahrtfahrradcompanion.util.location.Location
import nl.jjt.vorfahrtfahrradcompanion.util.location.LocationProvider

/** Hands out a last-known position and nothing else; streaming is an error the test wants to hear about. */
class FakeLocationProvider(var known: Location?) : LocationProvider {
    override fun locations(intervalMillis: Long): Flow<Location> = throw AssertionError("not streamed")
    override suspend fun lastKnown(): Location? = known
}
