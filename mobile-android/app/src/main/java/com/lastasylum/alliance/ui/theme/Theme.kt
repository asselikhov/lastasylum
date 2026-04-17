package com.lastasylum.alliance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SquadRelayInversePrimary = Color(0xFFCDB7FF)

private val SquadRelayColorScheme = darkColorScheme(
    primary = SquadRelayPrimary,
    onPrimary = SquadRelayOnPrimary,
    primaryContainer = SquadRelayMineBubble,
    onPrimaryContainer = SquadRelayMineOnBubble,
    inversePrimary = SquadRelayInversePrimary,
    secondary = SquadRelaySecondary,
    onSecondary = SquadRelayOnSecondary,
    secondaryContainer = SquadRelaySecondaryContainer,
    onSecondaryContainer = SquadRelayOnSecondaryContainer,
    tertiary = SquadRelayTertiary,
    onTertiary = SquadRelayOnTertiary,
    tertiaryContainer = SquadRelayTertiaryContainer,
    onTertiaryContainer = SquadRelayOnTertiaryContainer,
    background = SquadRelayBackground,
    onBackground = SquadRelayOnBackground,
    surface = SquadRelaySurface,
    onSurface = SquadRelayOnSurface,
    surfaceVariant = SquadRelaySurfaceVariant,
    onSurfaceVariant = SquadRelayOnSurfaceVariant,
    surfaceDim = SquadRelaySurfaceLow,
    surfaceBright = SquadRelaySurfaceHigh,
    surfaceContainerLowest = SquadRelaySurfaceLowest,
    surfaceContainerLow = SquadRelaySurfaceLow,
    surfaceContainer = SquadRelaySurface,
    surfaceContainerHigh = SquadRelaySurfaceHigh,
    surfaceContainerHighest = SquadRelaySurfaceHighest,
    error = SquadRelayError,
    onError = SquadRelayOnError,
    errorContainer = SquadRelayErrorContainer,
    onErrorContainer = SquadRelayOnErrorContainer,
    outline = SquadRelayOutline,
    outlineVariant = SquadRelayOutlineVariant,
    scrim = SquadRelayScrim,
    surfaceTint = Color.Transparent,
)

@Composable
fun SquadRelayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SquadRelayColorScheme,
        typography = SquadRelayTypography,
        shapes = SquadRelayShapes,
        content = content,
    )
}
