package com.geostamp.camera.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val CyanPrimary = Color(0xFF00E5FF)
val CyanVariant = Color(0xFF00B0FF)
val OnyxBackground = Color(0xFF0A0E14)
val OnyxSurface = Color(0xFF121820)
val OnyxSurfaceVariant = Color(0xFF1C2430)
val OnSurfaceWhite = Color(0xFFF0F4F8)
val OnSurfaceMedium = Color(0xFF90A4AE)
val AccentGreen = Color(0xFF00E676)
val AccentAmber = Color(0xFFFFD600)
val AccentRed = Color(0xFFFF5252)

val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color(0xFF00222B),
    primaryContainer = Color(0xFF004D5A),
    onPrimaryContainer = Color(0xFFA6EEFF),
    secondary = CyanVariant,
    onSecondary = Color(0xFF00243B),
    background = OnyxBackground,
    onBackground = OnSurfaceWhite,
    surface = OnyxSurface,
    onSurface = OnSurfaceWhite,
    surfaceVariant = OnyxSurfaceVariant,
    onSurfaceVariant = OnSurfaceMedium,
    error = AccentRed
)
