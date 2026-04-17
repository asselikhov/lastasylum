package com.lastasylum.alliance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SquadRelayColorScheme = darkColorScheme(
    primary = SquadRelayPrimary,
    onPrimary = SquadRelayOnPrimary,
    secondary = SquadRelaySecondary,
    background = SquadRelayBackground,
    surface = SquadRelaySurface,
    surfaceContainerHigh = SquadRelaySurfaceHigh,
    error = SquadRelayError,
)

@Composable
fun SquadRelayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SquadRelayColorScheme,
        typography = SquadRelayTypography,
        content = content,
    )
}
