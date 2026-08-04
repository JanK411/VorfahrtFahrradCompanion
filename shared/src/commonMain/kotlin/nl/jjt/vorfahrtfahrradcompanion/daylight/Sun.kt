package nl.jjt.vorfahrtfahrradcompanion.daylight

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.time.Instant

/**
 * What the sun does on one day at one place: it comes up, and it goes down.
 *
 * Beyond the polar circles it does neither, and the pair degenerates rather than branching. In the
 * polar night [sunrise] and [sunset] are both solar noon, so the day is an instant long; in the polar
 * day they are noon either side, so the day is the whole twenty-four hours and the night is the
 * instant between them.
 */
data class SolarDay(val sunrise: Instant, val sunset: Instant)

/**
 * Whether the sun is down at [at], at the given place — the whole point of which is that "after
 * sunset" is a different time in June than in December, and a different one in Groningen than in
 * Maastricht. A fixed hour would put the screen in night colours while it is still broad daylight.
 */
fun isNightAt(at: Instant, latitude: Double, longitude: Double): Boolean =
    with(solarDay(at, latitude, longitude)) { at < sunrise || at > sunset }

/**
 * Sunrise and sunset around the solar noon nearest [around], for [latitude] and [longitude] in
 * degrees, east and north positive.
 *
 * The standard sunrise equation, to the accuracy it is usually quoted at — a minute or so, which is
 * far inside what a screen's colours have to be right to. Rise and set are taken at the usual
 * −0.833°, the sun's centre a little below the horizon, so that refraction and the sun's own width
 * are accounted for.
 */
fun solarDay(around: Instant, latitude: Double, longitude: Double): SolarDay {
    // The cycle whose noon lies nearest the moment asked about, so the rise and set it yields are
    // the pair that brackets it rather than yesterday's or tomorrow's.
    val cycle = round(julianDay(around) - Epoch2000 - MeanNoonOffset + longitude / 360.0)

    // Mean solar noon at this longitude, in days since J2000: east of Greenwich the sun is overhead
    // before it is over Greenwich, four minutes to the degree.
    val meanTime = cycle + MeanNoonOffset - longitude / 360.0

    val anomaly = (357.5291 + 0.98560028 * meanTime).mod(360.0)
    val centre = 1.9148 * sinDeg(anomaly) + 0.0200 * sinDeg(2 * anomaly) + 0.0003 * sinDeg(3 * anomaly)

    // Where the sun is along the ecliptic, and from that how far it stands off the equator.
    val eclipticLongitude = (anomaly + centre + 180.0 + 102.9372).mod(360.0)
    val declination = asin(sinDeg(eclipticLongitude) * sinDeg(EarthTilt))

    val transit = Epoch2000 + meanTime + 0.0053 * sinDeg(anomaly) - 0.0069 * sinDeg(2 * eclipticLongitude)

    // How far either side of noon the sun crosses the horizon. Beyond the polar circles the horizon
    // is not crossed at all and this runs past ±1, where clamping gives the degenerate day: no width
    // at all when the sun stays down, the full turn when it stays up.
    val hourAngle = (sinDeg(HorizonDip) - sinDeg(latitude) * sin(declination)) /
        (cosDeg(latitude) * cos(declination))

    val half = toDegrees(acos(hourAngle.coerceIn(-1.0, 1.0))) / 360.0
    return SolarDay(sunrise = instantOf(transit - half), sunset = instantOf(transit + half))
}

/** J2000: noon on 2000-01-01, the epoch the whole calculation counts days from. */
private const val Epoch2000 = 2451545.0

/** The fractional-day correction that puts mean solar noon where the equation expects it. */
private const val MeanNoonOffset = 0.0009

private const val EarthTilt = 23.4397

/** Where the sun's centre stands at the moment it counts as risen or set. */
private const val HorizonDip = -0.833

private const val SecondsPerDay = 86_400.0

/** Julian dates count from noon on 1 January 4713 BC; the unix epoch falls this far along. */
private const val UnixEpochJulianDay = 2440587.5

private fun julianDay(at: Instant): Double =
    at.toEpochMilliseconds() / (SecondsPerDay * 1000.0) + UnixEpochJulianDay

private fun instantOf(julianDay: Double): Instant =
    Instant.fromEpochMilliseconds(((julianDay - UnixEpochJulianDay) * SecondsPerDay * 1000.0).toLong())

private fun sinDeg(degrees: Double) = sin(degrees * DegreesToRadians)

private fun cosDeg(degrees: Double) = cos(degrees * DegreesToRadians)

private fun toDegrees(radians: Double) = radians / DegreesToRadians

private const val DegreesToRadians = PI / 180.0
