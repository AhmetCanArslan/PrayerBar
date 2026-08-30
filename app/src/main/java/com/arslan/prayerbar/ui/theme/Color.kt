package com.arslan.prayerbar.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Seeded on a deep teal-green with a warm gold tertiary — the fallback when the device has no
// dynamic color to offer. Roles are paired, never mixed ad hoc.
private val Teal40 = Color(0xFF00696B)
private val Teal90 = Color(0xFF9CF1F0)
private val Teal10 = Color(0xFF002020)
private val Teal80 = Color(0xFF80D5D4)
private val Teal30 = Color(0xFF004F50)
private val Teal20 = Color(0xFF003737)

private val Sage40 = Color(0xFF4A6363)
private val Sage90 = Color(0xFFCCE8E7)
private val Sage10 = Color(0xFF051F1F)
private val Sage80 = Color(0xFFB1CCCB)
private val Sage30 = Color(0xFF324B4B)
private val Sage20 = Color(0xFF1B3435)

private val Gold40 = Color(0xFF7C5800)
private val Gold90 = Color(0xFFFFDEA6)
private val Gold10 = Color(0xFF271900)
private val Gold80 = Color(0xFFF7BD48)
private val Gold30 = Color(0xFF5E4200)
private val Gold20 = Color(0xFF412D00)

val PrayerBarLightScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal10,
    secondary = Sage40,
    onSecondary = Color.White,
    secondaryContainer = Sage90,
    onSecondaryContainer = Sage10,
    tertiary = Gold40,
    onTertiary = Color.White,
    tertiaryContainer = Gold90,
    onTertiaryContainer = Gold10,
    background = Color(0xFFFAFDFC),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE5E4),
    onSurfaceVariant = Color(0xFF3F4949),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F7F6),
    surfaceContainer = Color(0xFFEEF2F1),
    surfaceContainerHigh = Color(0xFFE8ECEB),
    surfaceContainerHighest = Color(0xFFE2E6E5),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC9C8),
)

val PrayerBarDarkScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal20,
    primaryContainer = Teal30,
    onPrimaryContainer = Teal90,
    secondary = Sage80,
    onSecondary = Sage20,
    secondaryContainer = Sage30,
    onSecondaryContainer = Sage90,
    tertiary = Gold80,
    onTertiary = Gold20,
    tertiaryContainer = Gold30,
    onTertiaryContainer = Gold90,
    background = Color(0xFF101414),
    onBackground = Color(0xFFE0E3E2),
    surface = Color(0xFF101414),
    onSurface = Color(0xFFE0E3E2),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC9C8),
    surfaceContainerLowest = Color(0xFF0B0F0F),
    surfaceContainerLow = Color(0xFF191C1C),
    surfaceContainer = Color(0xFF1D2020),
    surfaceContainerHigh = Color(0xFF272B2B),
    surfaceContainerHighest = Color(0xFF323535),
    outline = Color(0xFF899393),
    outlineVariant = Color(0xFF3F4949),
)
