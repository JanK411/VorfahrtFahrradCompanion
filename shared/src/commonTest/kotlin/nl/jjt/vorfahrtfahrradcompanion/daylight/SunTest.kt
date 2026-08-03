package nl.jjt.vorfahrtfahrradcompanion.daylight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Amsterdam, where the difference between midsummer and midwinter is over nine hours of daylight. */
private const val AmsterdamLatitude = 52.37
private const val AmsterdamLongitude = 4.90

/** Tromsø, north of the Arctic Circle: no sunset in June, no sunrise in December. */
private const val TromsoLatitude = 69.65
private const val TromsoLongitude = 18.96

/** Published rise and set times are quoted to the minute; a few either way is neither here nor there. */
private val Tolerance = 4.minutes

class SunTest {

    @Test
    fun midsummerInAmsterdamRunsFromQuarterPastFiveToJustAfterTen() {
        val day = risesAndSets("2026-06-21T12:00:00Z")

        // 05:19 and 22:06 local, which is CEST — two hours ahead of the UTC asserted here.
        assertCloseTo(Instant.parse("2026-06-21T03:19:00Z"), day.sunrise)
        assertCloseTo(Instant.parse("2026-06-21T20:06:00Z"), day.sunset)
        assertCloseTo(16.hours + 47.minutes, day.sunset - day.sunrise)
    }

    @Test
    fun midwinterInAmsterdamIsBarelyOverSevenAndAHalfHoursLong() {
        val day = risesAndSets("2026-12-21T12:00:00Z")

        // 08:48 and 16:30 local, CET, one hour ahead of UTC.
        assertCloseTo(Instant.parse("2026-12-21T07:48:00Z"), day.sunrise)
        assertCloseTo(Instant.parse("2026-12-21T15:30:00Z"), day.sunset)
        assertCloseTo(7.hours + 42.minutes, day.sunset - day.sunrise)
    }

    @Test
    fun aSummerEveningIsStillDayLongAfterTheDeviceWouldHaveDimmedItself() {
        // Half past nine in the evening, local: dark by any fixed hour, and an hour of daylight left.
        assertFalse(night("2026-06-21T19:30:00Z"))
        assertTrue(night("2026-06-21T21:00:00Z"))
    }

    @Test
    fun aWinterEveningIsNightWellBeforeThat() {
        // Half past five, local, in December — dark for an hour by then.
        assertTrue(night("2026-12-21T16:30:00Z"))
        assertFalse(night("2026-12-21T12:00:00Z"))
    }

    @Test
    fun theSunRisesAndSetsOnBothSidesOfMidnight() {
        // Around the turn of the day the nearest noon is yesterday's or tomorrow's; either way the
        // answer has to be "night" rather than a rise and set that bracket the wrong day.
        assertTrue(night("2026-06-21T23:59:00Z"))
        assertTrue(night("2026-06-22T00:01:00Z"))
        assertTrue(night("2026-12-21T23:59:00Z"))
        assertTrue(night("2026-12-22T00:01:00Z"))
    }

    @Test
    fun aboveTheArcticCircleTheSunCanStayUpOrStayDownAllDay() {
        val midsummer = Instant.parse("2026-06-21T12:00:00Z")
        val midwinter = Instant.parse("2026-12-21T12:00:00Z")

        assertEquals(SolarDay.PolarDay, solarDay(midsummer, TromsoLatitude, TromsoLongitude))
        assertEquals(SolarDay.PolarNight, solarDay(midwinter, TromsoLatitude, TromsoLongitude))

        // Midnight in Tromsø in June is daylight, and noon in December is not.
        assertFalse(isNightAt(midsummer, TromsoLatitude, TromsoLongitude))
        assertTrue(isNightAt(midwinter, TromsoLatitude, TromsoLongitude))
    }

    @Test
    fun theEquatorGetsRoughlyTwelveHoursOfDaylightWhateverTheDate() {
        listOf("2026-03-21T12:00:00Z", "2026-06-21T12:00:00Z", "2026-12-21T12:00:00Z").forEach { date ->
            val day = solarDay(Instant.parse(date), latitude = 0.0, longitude = 0.0)
            assertIs<SolarDay.RisesAndSets>(day)
            assertCloseTo(12.hours + 7.minutes, day.sunset - day.sunrise)
        }
    }

    private fun risesAndSets(at: String): SolarDay.RisesAndSets {
        val day = solarDay(Instant.parse(at), AmsterdamLatitude, AmsterdamLongitude)
        assertIs<SolarDay.RisesAndSets>(day)
        return day
    }

    private fun night(at: String) =
        isNightAt(Instant.parse(at), AmsterdamLatitude, AmsterdamLongitude)

    private fun assertCloseTo(expected: Instant, actual: Instant) =
        assertTrue((expected - actual).absoluteValue < Tolerance, "$actual, expected $expected")

    private fun assertCloseTo(expected: Duration, actual: Duration) =
        assertTrue((expected - actual).absoluteValue < Tolerance, "$actual, expected $expected")
}
