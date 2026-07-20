package nl.jjt.vorfahrtfahrradcompanion.location

import kotlin.time.Instant

data class Location(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float?,
    val altitudeMeters: Double?,
    val timestamp: Instant,
)
