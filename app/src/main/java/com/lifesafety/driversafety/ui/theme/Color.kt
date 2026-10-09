package com.lifesafety.driversafety.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Brand colours. One blue for the brand, green for "on trip / good", amber for "offline / attention",
 * red for the alarm and errors. Neutrals are cool greys so the app does not take Material's purple tint.
 * Light and dark are designed together: each role has a pair that keeps the same meaning and contrast.
 */
val AlarmRed = Color(0xFFB3261E)
val OnAlarmRed = Color(0xFFFFFFFF)

val LightColors = lightColorScheme(
    primary = Color(0xFF0B3D91),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A42),
    secondary = Color(0xFF006C4C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF8AF7C5),
    onSecondaryContainer = Color(0xFF002115),
    tertiary = Color(0xFF7A4E00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF271900),
    error = AlarmRed,
    onError = OnAlarmRed,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF1A1C20),
    surface = Color(0xFFF5F7FB),
    onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFE1E3EB),
    onSurfaceVariant = Color(0xFF444750),
    outline = Color(0xFF747781),
    outlineVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEEF0F6),
    surfaceContainerHigh = Color(0xFFE8EAF1),
    surfaceContainerHighest = Color(0xFFE1E3EB)
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E6A),
    primaryContainer = Color(0xFF0B3D91),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFF6EDBAA),
    onSecondary = Color(0xFF003826),
    secondaryContainer = Color(0xFF005138),
    onSecondaryContainer = Color(0xFF8AF7C5),
    tertiary = Color(0xFFF5BD6A),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C3F00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF444750),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF444750),
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF191B20),
    surfaceContainer = Color(0xFF1D1F24),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A)
)
