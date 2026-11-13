package com.lion.haberakisi.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Red = Color(0xFFD32F2F)
private val RedDark = Color(0xFFEF5350)

private val LightColors = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFF775652),
    surface = Color(0xFFFFF8F7),
)
private val DarkColors = darkColorScheme(
    primary = RedDark,
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF8B1A17),
    secondary = Color(0xFFE7BDB7),
    surface = Color(0xFF171210),
)

@Composable
fun HaberAkisiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
