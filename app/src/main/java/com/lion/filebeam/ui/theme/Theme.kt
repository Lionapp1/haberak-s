package com.lion.filebeam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Tek marka paleti: telefon, tablet ve Android TV bütün ekranlarda aynı semantik renkleri kullanır.
private val BeamBlue = Color(0xFF246BCE)
private val BeamCyan = Color(0xFF55D6E8)
private val FileBeamShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)
private val LightColors = lightColorScheme(
    primary = BeamBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9FF),
    onPrimaryContainer = Color(0xFF102E59),
    secondary = Color(0xFF176B63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F2EC),
    onSecondaryContainer = Color(0xFF073B36),
    tertiary = Color(0xFF7656A8),
    onTertiary = Color.White,
    background = Color(0xFFF5F7FC),
    onBackground = Color(0xFF171B24),
    surface = Color(0xFFF9FAFE),
    onSurface = Color(0xFF171B24),
    surfaceVariant = Color(0xFFE8EDF6),
    onSurfaceVariant = Color(0xFF465365),
    outline = Color(0xFF78869A),
    outlineVariant = Color(0xFFD3DBE8),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)
private val DarkColors = darkColorScheme(
    primary = BeamCyan,
    onPrimary = Color(0xFF003640),
    primaryContainer = Color(0xFF164E65),
    onPrimaryContainer = Color(0xFFD0F5FF),
    secondary = Color(0xFF70D8C9),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF15534C),
    onSecondaryContainer = Color(0xFFC8F5EC),
    tertiary = Color(0xFFD2B7FF),
    onTertiary = Color(0xFF3B2167),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF050505),
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF171717),
    onSurfaceVariant = Color(0xFFB8C4D5),
    outline = Color(0xFF8492A5),
    outlineVariant = Color(0xFF292929),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun FileBeamTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = scheme, shapes = FileBeamShapes, content = content)
}
