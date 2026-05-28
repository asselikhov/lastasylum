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
import com.lastasylum.alliance.ui.components.team.TeamFeedCardTokens

/** Frosted-style panel — delegates to premium glass layer. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    shadowElevation: Dp = 8.dp,
    showInnerGlow: Boolean = false,
    layerAlpha: Float? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val resolvedShape = shape ?: com.lastasylum.alliance.ui.theme.premium.PremiumCardShape
    com.lastasylum.alliance.ui.components.premium.PremiumGlassSurface(
        modifier = modifier,
        shape = resolvedShape,
        shadowElevation = shadowElevation,
        showInnerGlow = showInnerGlow,
        layerAlpha = layerAlpha ?: com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces.layer1Alpha,
        highlightCornerRadius = TeamFeedCardTokens.cardRadius,
        content = content,
    )
}
