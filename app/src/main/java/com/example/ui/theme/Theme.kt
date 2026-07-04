package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = PulsePrimary,
    onPrimary = Color(0xFF06122F),
    primaryContainer = Color(0xFF2A3B7A),
    onPrimaryContainer = Color(0xFFE3E9FF),
    secondary = PulseSecondary,
    onSecondary = Color(0xFF06122F),
    tertiary = PulseSuccess,
    background = PulseBackground,
    onBackground = PulseOnSurface,
    surface = PulseCard,
    onSurface = PulseOnSurface,
    surfaceVariant = PulseCardElevated,
    onSurfaceVariant = PulseOnSurfaceMuted,
    surfaceContainerHigh = PulseCardElevated,
    surfaceContainerLowest = PulseBackground,
    outline = PulseOutline,
    error = PulseDanger,
)

private val LightColorScheme = lightColorScheme(
    primary = PulsePrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF17235C),
    secondary = PulseSecondary,
    tertiary = PulseSuccess,
    background = PulseLightBackground,
    onBackground = PulseLightOnSurface,
    surface = PulseLightCard,
    onSurface = PulseLightOnSurface,
    surfaceVariant = Color(0xFFEDEFF7),
    onSurfaceVariant = PulseLightOnSurfaceMuted,
    surfaceContainerHigh = Color(0xFFEDEFF7),
    surfaceContainerLowest = PulseLightBackground,
    error = PulseDanger,
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic (wallpaper-based) color is OFF by default: ScreenPulse has its own fixed
  // brand palette and should look the same on every device regardless of wallpaper.
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
