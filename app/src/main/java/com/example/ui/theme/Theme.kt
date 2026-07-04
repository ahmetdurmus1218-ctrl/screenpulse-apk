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

private val CosmicDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1B3380),
    onPrimaryContainer = Color(0xFFD6E0FF),
    secondary = ElectricCyan,
    onSecondary = Color.Black,
    tertiary = ElectricGreen,
    onTertiary = Color.Black,
    background = CosmicBackground,
    onBackground = TextPrimary,
    surface = CosmicSurface,
    onSurface = TextPrimary,
    surfaceVariant = CosmicSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = CosmicBackground,
    surfaceContainerLow = CosmicSurface,
    surfaceContainer = CosmicSurface,
    surfaceContainerHigh = CosmicSurfaceElevated,
    surfaceContainerHighest = CosmicBorder,
    outline = CosmicBorder,
    outlineVariant = CosmicBorder.copy(alpha = 0.5f),
    error = ElectricRed,
)

private val CosmicLightColorScheme = lightColorScheme(
    primary = ElectricBlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF14204F),
    secondary = ElectricBlueLight,
    tertiary = ElectricGreen,
    background = CosmicLightBackground,
    onBackground = LightTextPrimary,
    surface = CosmicLightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = Color(0xFFEDEFF7),
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = CosmicLightBackground,
    surfaceContainerHigh = Color(0xFFEDEFF7),
    outline = CosmicLightBorder,
    error = ElectricRed,
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic (wallpaper-based) color is OFF by default: ScreenPulse has its own fixed
  // "Cosmic" brand palette and should look the same on every device regardless of wallpaper.
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> CosmicDarkColorScheme
      else -> CosmicLightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
