package com.muzrc.klipsync.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = PrimaryCyan,
    onPrimary = Color(0xFF001F28),
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = CyanOnContainer,
    secondary = PrimaryCyan,
    onSecondary = Color(0xFF001F28),
    secondaryContainer = CyanContainerDark,
    onSecondaryContainer = CyanOnContainer,
    tertiary = PrimaryCyan,
    onTertiary = Color(0xFF001F28),
    tertiaryContainer = CyanContainerDark,
    onTertiaryContainer = CyanOnContainer,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = DarkBackground,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = SurfaceElevatedHigh,
    outline = StrongBorder,
    outlineVariant = SubtleBorder,
    error = ErrorRed,
    onError = Color(0xFF2B0004)
)

@Composable
fun KlipSyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content
    )
}
