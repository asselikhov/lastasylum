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

/** Frosted-style panel: translucent fill + hairline border (no backdrop blur; stable on API 28+). */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    shadowElevation: Dp = 6.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val resolvedShape = shape ?: MaterialTheme.shapes.large
    val fill = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    val edge = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
    Surface(
        modifier = modifier,
        shape = resolvedShape,
        color = fill,
        tonalElevation = 0.dp,
        shadowElevation = shadowElevation,
        border = BorderStroke(1.dp, edge),
    ) {
        Box(content = content)
    }
}
