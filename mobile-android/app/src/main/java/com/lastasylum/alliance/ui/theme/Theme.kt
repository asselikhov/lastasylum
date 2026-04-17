package com.lastasylum.alliance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SquadRelayColorScheme = darkColorScheme(
    primary = SquadRelayPrimary,
    onPrimary = SquadRelayOnPrimary,
    primaryContainer = SquadRelayMineBubble,
    onPrimaryContainer = SquadRelayMineOnBubble,
    secondary = SquadRelaySecondary,
    onSecondary = SquadRelayOnSecondary,
    secondaryContainer = SquadRelaySurfaceVariant,
    onSecondaryContainer = SquadRelaySecondary,
    tertiary = SquadRelayTertiary,
    onTertiary = SquadRelayOnTertiary,
    background = SquadRelayBackground,
    onBackground = SquadRelayOnBackground,
    surface = SquadRelaySurface,
    onSurface = SquadRelayOnSurface,
    surfaceVariant = SquadRelaySurfaceVariant,
    onSurfaceVariant = SquadRelayOnSurfaceVariant,
    surfaceContainer = SquadRelaySurface,
    surfaceContainerHigh = SquadRelaySurfaceHigh,
    error = SquadRelayError,
    onError = SquadRelayOnError,
    outline = SquadRelayOutline,
    outlineVariant = SquadRelayOutlineVariant,
)

@Composable
fun SquadRelayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SquadRelayColorScheme,
        typography = SquadRelayTypography,
        content = content,
    )
}
