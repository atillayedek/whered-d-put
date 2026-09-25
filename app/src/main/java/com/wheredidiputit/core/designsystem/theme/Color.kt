package com.wheredidiputit.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Calm, warm neutrals with a muted sage accent. Pure black and pure white are
 * deliberately avoided on large surfaces. All text pairs meet WCAG AA contrast.
 */
object WdipiColors {
    // Light
    val LightBackground = Color(0xFFF6F4EF)
    val LightSurfaceLowest = Color(0xFFFEFDFA)
    val LightSurfaceLow = Color(0xFFF9F7F2)
    val LightSurfaceContainer = Color(0xFFF0EEE8)
    val LightSurfaceHigh = Color(0xFFEAE8E1)
    val LightSurfaceHighest = Color(0xFFE4E2DA)
    val LightOnSurface = Color(0xFF1E1F1C)
    val LightOnSurfaceVariant = Color(0xFF5B5E57)
    val LightOutline = Color(0xFF8A8D84)
    val LightOutlineVariant = Color(0xFFDAD7CE)
    val LightPrimary = Color(0xFF4E6B53)
    val LightOnPrimary = Color(0xFFFBFCF8)
    val LightPrimaryContainer = Color(0xFFD5E4D2)
    val LightOnPrimaryContainer = Color(0xFF122917)
    val LightSecondary = Color(0xFF5A6356)
    val LightSecondaryContainer = Color(0xFFDFE5D9)
    val LightOnSecondaryContainer = Color(0xFF181E16)
    val LightTertiary = Color(0xFF7A5E4B)
    val LightError = Color(0xFFB0443A)
    val LightOnError = Color(0xFFFFFBFA)
    val LightErrorContainer = Color(0xFFF8DDD8)
    val LightOnErrorContainer = Color(0xFF45100B)

    // Dark
    val DarkBackground = Color(0xFF1B1D1B)
    val DarkSurfaceLowest = Color(0xFF161816)
    val DarkSurfaceLow = Color(0xFF222522)
    val DarkSurfaceContainer = Color(0xFF262926)
    val DarkSurfaceHigh = Color(0xFF2D302C)
    val DarkSurfaceHighest = Color(0xFF363934)
    val DarkOnSurface = Color(0xFFE6E3DC)
    val DarkOnSurfaceVariant = Color(0xFFB7B6AE)
    val DarkOutline = Color(0xFF8E918A)
    val DarkOutlineVariant = Color(0xFF41443F)
    val DarkPrimary = Color(0xFFA8C5A8)
    val DarkOnPrimary = Color(0xFF15311D)
    val DarkPrimaryContainer = Color(0xFF304B36)
    val DarkOnPrimaryContainer = Color(0xFFCBE4C8)
    val DarkSecondary = Color(0xFFBEC7B8)
    val DarkSecondaryContainer = Color(0xFF383D35)
    val DarkOnSecondaryContainer = Color(0xFFDDE3D6)
    val DarkTertiary = Color(0xFFDBBFAA)
    val DarkError = Color(0xFFF0A79F)
    val DarkOnError = Color(0xFF561712)
    val DarkErrorContainer = Color(0xFF762B23)
    val DarkOnErrorContainer = Color(0xFFFFDAD5)
}

internal val LightColorScheme: ColorScheme = lightColorScheme(
    primary = WdipiColors.LightPrimary,
    onPrimary = WdipiColors.LightOnPrimary,
    primaryContainer = WdipiColors.LightPrimaryContainer,
    onPrimaryContainer = WdipiColors.LightOnPrimaryContainer,
    secondary = WdipiColors.LightSecondary,
    onSecondary = WdipiColors.LightOnPrimary,
    secondaryContainer = WdipiColors.LightSecondaryContainer,
    onSecondaryContainer = WdipiColors.LightOnSecondaryContainer,
    tertiary = WdipiColors.LightTertiary,
    onTertiary = WdipiColors.LightOnPrimary,
    background = WdipiColors.LightBackground,
    onBackground = WdipiColors.LightOnSurface,
    surface = WdipiColors.LightBackground,
    onSurface = WdipiColors.LightOnSurface,
    surfaceVariant = WdipiColors.LightSurfaceHigh,
    onSurfaceVariant = WdipiColors.LightOnSurfaceVariant,
    surfaceTint = WdipiColors.LightPrimary,
    surfaceBright = WdipiColors.LightSurfaceLowest,
    surfaceDim = WdipiColors.LightSurfaceHighest,
    surfaceContainerLowest = WdipiColors.LightSurfaceLowest,
    surfaceContainerLow = WdipiColors.LightSurfaceLow,
    surfaceContainer = WdipiColors.LightSurfaceContainer,
    surfaceContainerHigh = WdipiColors.LightSurfaceHigh,
    surfaceContainerHighest = WdipiColors.LightSurfaceHighest,
    outline = WdipiColors.LightOutline,
    outlineVariant = WdipiColors.LightOutlineVariant,
    error = WdipiColors.LightError,
    onError = WdipiColors.LightOnError,
    errorContainer = WdipiColors.LightErrorContainer,
    onErrorContainer = WdipiColors.LightOnErrorContainer,
    inverseSurface = WdipiColors.DarkSurfaceHigh,
    inverseOnSurface = WdipiColors.DarkOnSurface,
    inversePrimary = WdipiColors.DarkPrimary,
    scrim = Color(0xFF10110F),
)

internal val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = WdipiColors.DarkPrimary,
    onPrimary = WdipiColors.DarkOnPrimary,
    primaryContainer = WdipiColors.DarkPrimaryContainer,
    onPrimaryContainer = WdipiColors.DarkOnPrimaryContainer,
    secondary = WdipiColors.DarkSecondary,
    onSecondary = WdipiColors.DarkOnPrimary,
    secondaryContainer = WdipiColors.DarkSecondaryContainer,
    onSecondaryContainer = WdipiColors.DarkOnSecondaryContainer,
    tertiary = WdipiColors.DarkTertiary,
    onTertiary = WdipiColors.DarkOnPrimary,
    background = WdipiColors.DarkBackground,
    onBackground = WdipiColors.DarkOnSurface,
    surface = WdipiColors.DarkBackground,
    onSurface = WdipiColors.DarkOnSurface,
    surfaceVariant = WdipiColors.DarkSurfaceHigh,
    onSurfaceVariant = WdipiColors.DarkOnSurfaceVariant,
    surfaceTint = WdipiColors.DarkPrimary,
    surfaceBright = WdipiColors.DarkSurfaceHighest,
    surfaceDim = WdipiColors.DarkSurfaceLowest,
    surfaceContainerLowest = WdipiColors.DarkSurfaceLowest,
    surfaceContainerLow = WdipiColors.DarkSurfaceLow,
    surfaceContainer = WdipiColors.DarkSurfaceContainer,
    surfaceContainerHigh = WdipiColors.DarkSurfaceHigh,
    surfaceContainerHighest = WdipiColors.DarkSurfaceHighest,
    outline = WdipiColors.DarkOutline,
    outlineVariant = WdipiColors.DarkOutlineVariant,
    error = WdipiColors.DarkError,
    onError = WdipiColors.DarkOnError,
    errorContainer = WdipiColors.DarkErrorContainer,
    onErrorContainer = WdipiColors.DarkOnErrorContainer,
    inverseSurface = WdipiColors.LightSurfaceHigh,
    inverseOnSurface = WdipiColors.LightOnSurface,
    inversePrimary = WdipiColors.LightPrimary,
    scrim = Color(0xFF000000),
)
