package nl.jjt.vorfahrtfahrradcompanion.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import nl.jjt.vorfahrtfahrradcompanion.ui.common.SystemBarIcons

/**
 * The colours of the one element under the rider's thumb, and of nothing else — see [Spotlight].
 *
 * The same calm cycling green as the rest of the app, but pitched dark and saturated against a
 * near-white background, because this is what has to be read in sunlight from a holder on the
 * handlebars. Every colour that carries text or an edge clears 7:1 against what it sits on, well
 * past the 4.5:1 that counts as legible indoors: a phone at half brightness under a bright sky
 * loses most of the difference between two mid-tones.
 */
private val SpotlightColors = lightColorScheme(
    primary = Color(0xFF14532D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA6E9B7),
    onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFF2C4636),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC6E4CC),
    onSecondaryContainer = Color(0xFF07160C),
    tertiary = Color(0xFF7A4100),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD6AF),
    onTertiaryContainer = Color(0xFF2C1600),
    error = Color(0xFF9C1010),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFD6D1),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFCFDFB),
    onBackground = Color(0xFF0C110C),
    surface = Color(0xFFFCFDFB),
    onSurface = Color(0xFF0C110C),
    surfaceVariant = Color(0xFFE4EDE2),
    onSurfaceVariant = Color(0xFF232B23),
    // Dark enough to be an edge rather than a hint: an outlined button is a button in sunlight too.
    outline = Color(0xFF3D453D),
    outlineVariant = Color(0xFF9AA69A),
)

/** Everything else, at every hour: "go" green as the lead, a hi-vis amber accent. */
private val AppColors = darkColorScheme(
    primary = Color(0xFF96D5A3),
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF14522D),
    onPrimaryContainer = Color(0xFFB1F1BE),
    secondary = Color(0xFFB6CCB9),
    onSecondary = Color(0xFF223527),
    secondaryContainer = Color(0xFF384B3C),
    onSecondaryContainer = Color(0xFFD2E8D4),
    tertiary = Color(0xFFFFB870),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6A3C00),
    onTertiaryContainer = Color(0xFFFFDCBE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101510),
    onBackground = Color(0xFFDFE4DB),
    surface = Color(0xFF101510),
    onSurface = Color(0xFFDFE4DB),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC0C9BF),
    outline = Color(0xFF8A938A),
)

/** Whether the sun is down where the rider is — see [nl.jjt.vorfahrtfahrradcompanion.util.daylight.Daylight]. */
val LocalNight = staticCompositionLocalOf { false }

/**
 * The app's colours: dark, all of it, day and night. Only the one element asking the rider for an
 * answer is lit, and against a dark screen that one is found at a glance rather than searched for
 * among a page of equally bright ones — see [Spotlight].
 *
 * [night] does not decide the scheme, then; it decides whether the lit element is lit at all. A
 * white card in the dark is a torch in the face, and the rider's eyes have the road to get back to.
 */
@Composable
fun AppTheme(night: Boolean = false, content: @Composable () -> Unit) {
    // The system draws its own bars over what the app paints, which is dark whatever the hour.
    SystemBarIcons(dark = true)

    CompositionLocalProvider(LocalNight provides night) {
        MaterialTheme(colorScheme = AppColors, content = content)
    }
}

/**
 * Lights [content] up against the dark the rest of the screen is in — for the one element the rider
 * is dealing with right now, and never for more than one at a time.
 *
 * A daylight measure: after dark the surrounding scheme is kept, since a screen read by a rider
 * whose eyes are set for an unlit road needs no help standing out.
 */
@Composable
fun Spotlight(lit: Boolean, content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (lit && !LocalNight.current) SpotlightColors else MaterialTheme.colorScheme,
    content = content,
)
