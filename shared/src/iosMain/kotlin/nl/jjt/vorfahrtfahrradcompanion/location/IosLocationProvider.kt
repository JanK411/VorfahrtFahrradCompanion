package nl.jjt.vorfahrtfahrradcompanion.location

import kotlinx.coroutines.flow.Flow

class IosLocationProvider : LocationProvider {
    override fun locations(intervalMillis: Long): Flow<Location> = TODO("iOS not implemented")
    override suspend fun lastKnown(): Location? = TODO("iOS not implemented")
}
