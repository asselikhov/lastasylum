package com.lastasylum.alliance.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces

/** Frosted-style panel: единый тон с остальным UI (no backdrop blur; stable on API 28+). */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    shadowElevation: Dp = 3.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val resolvedShape = shape ?: MaterialTheme.shapes.large
    Surface(
        modifier = modifier,
        shape = resolvedShape,
        color = SquadRelaySurfaces.barColor(),
        tonalElevation = 0.dp,
        shadowElevation = shadowElevation,
        border = SquadRelaySurfaces.panelBorder(),
    ) {
        Box(content = content)
    }
}
