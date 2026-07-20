package nl.jjt.vorfahrtfahrradcompanion.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A calm, cycling-inspired green palette: "go" green as the lead, a hi-vis amber accent.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6B43),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB1F1BE),
    onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFF4F6353),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E8D4),
    onSecondaryContainer = Color(0xFF0D1F13),
    tertiary = Color(0xFF8B5000),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2C1600),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FBF3),
    onBackground = Color(0xFF181D18),
    surface = Color(0xFFF6FBF3),
    onSurface = Color(0xFF181D18),
    surfaceVariant = Color(0xFFDCE5DB),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717971),
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

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
