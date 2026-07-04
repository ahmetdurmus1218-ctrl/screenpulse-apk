package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ScreenPulse "Cosmic" brand palette — fixed, does not depend on device wallpaper.
val CosmicBackground = Color(0xFF08090D) // Pitch dark slate
val CosmicSurface = Color(0xFF12141C)    // Dark card background
val CosmicSurfaceElevated = Color(0xFF181B26)
val CosmicBorder = Color(0xFF1F2231)     // Subtle stroke
val ElectricBlue = Color(0xFF2B66FF)     // Vibrant cobalt blue primary
val ElectricCyan = Color(0xFF00E5FF)     // Secondary accent
val ElectricAmber = Color(0xFFFFA000)    // Warning/secondary accent
val ElectricGreen = Color(0xFF00C853)    // Positive accent
val ElectricRed = Color(0xFFFF5252)      // Danger accent
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF90A4AE)

// Light theme counterpart (kept so the dark/light toggle still does something real;
// same accent hues, adapted for a light background).
val CosmicLightBackground = Color(0xFFF6F7FB)
val CosmicLightSurface = Color(0xFFFFFFFF)
val CosmicLightBorder = Color(0xFFE1E4EC)
val ElectricBlueLight = Color(0xFF2B54D6)
val LightTextPrimary = Color(0xFF10121A)
val LightTextSecondary = Color(0xFF5C6270)

// Legacy names kept in case anything still references them
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
