package nl.jjt.vorfahrtfahrradcompanion.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * A calm, cycling-inspired green palette: "go" green as the lead, a hi-vis amber accent — pitched
 * dark and saturated against a near-white background, because this screen is read in sunlight from
 * a holder on the handlebars. Every colour that carries text or an edge clears 7:1 against what it
 * sits on, well past the 4.5:1 that counts as legible indoors: a phone at half brightness under a
 * bright sky loses most of the difference between two mid-tones.
 */
private val LightColors = lightColorScheme(
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

private val DarkColors = darkColorScheme(
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

/**
 * The app's colours. Dark only when it is actually dark out — see [nl.jjt.vorfahrtfahrradcompanion
 * .daylight.Daylight] — never because the device is set that way: a phone kept on dark all year
 * would otherwise hand the rider the dimmest screen exactly when the sun is brightest.
 */
@Composable
fun AppTheme(night: Boolean = false, content: @Composable () -> Unit) {
    // The system draws its own bars over what the app paints, and it has no idea which of the two
    // schemes is up, so it is told.
    SystemBarIcons(night)

    MaterialTheme(
        colorScheme = if (night) DarkColors else LightColors,
        content = content,
    )
}
