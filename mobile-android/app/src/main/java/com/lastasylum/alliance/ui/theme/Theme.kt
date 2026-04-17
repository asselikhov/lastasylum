package com.lastasylum.alliance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LastAsylumColorScheme = darkColorScheme(
    primary = ObzPrimary,
    onPrimary = ObzOnPrimary,
    secondary = ObzSecondary,
    background = ObzBackground,
    surface = ObzSurface,
    surfaceContainerHigh = ObzSurfaceHigh,
    error = ObzError,
)

@Composable
fun LastAsylumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LastAsylumColorScheme,
        typography = LastAsylumTypography,
        content = content,
    )
}
