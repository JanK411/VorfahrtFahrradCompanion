package nl.jjt.vorfahrtfahrradcompanion.util.daylight

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import nl.jjt.vorfahrtfahrradcompanion.util.location.Location
import nl.jjt.vorfahrtfahrradcompanion.util.location.LocationProvider
import nl.jjt.vorfahrtfahrradcompanion.testing.FakeClock
import nl.jjt.vorfahrtfahrradcompanion.testing.FakeLocationProvider
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.time.Instant

private val amsterdam = Location(
    latitude = 52.37,
    longitude = 4.90,
    accuracyMeters = 12f,
    speedMetersPerSecond = null,
    altitudeMeters = null,
    timestamp = Instant.parse("2026-06-21T12:00:00Z"),
)


class DaylightTest {

    @Test
    fun withoutAPositionItIsTakenToBeDay() = runTest {
        val daylight = Daylight(FakeLocationProvider(null), FakeClock(Instant.parse("2026-06-21T23:00:00Z")))

        // Midnight in Amsterdam, but nothing says the rider is there — and a screen that guesses
        // wrong towards dark is unreadable, where one that guesses wrong towards light is not.
        assertFalse(daylight.isNight.first())
    }

    @Test
    fun aPositionMakesItTheSunThatDecides() = runTest {
        val provider = FakeLocationProvider(amsterdam)

        assertFalse(Daylight(provider, FakeClock(Instant.parse("2026-06-21T19:30:00Z"))).isNight.first())
        assertTrue(Daylight(provider, FakeClock(Instant.parse("2026-06-21T23:00:00Z"))).isNight.first())
    }

    @Test
    fun aPositionThatGoesAwayIsStillTheBestOneThereIs() = runTest {
        val provider = FakeLocationProvider(amsterdam)
        val daylight = Daylight(provider, FakeClock(Instant.parse("2026-06-21T23:00:00Z")))
        assertTrue(daylight.isNight.first())

        // A fix lost between one look and the next says nothing about where the rider is now.
        provider.known = null
        assertTrue(daylight.isNight.first())
    }
}
