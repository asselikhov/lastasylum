package com.lastasylum.alliance.ui.components.premium

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.premium.PremiumCardShape
import com.lastasylum.alliance.ui.theme.premium.PremiumGlow
import com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces

/**
 * Layered glass panel: inner highlight + border. API 31+ may use blur in future;
 * API 28–30 uses rich faux-glass for stable overlay performance.
 */
@Composable
fun PremiumGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = PremiumCardShape,
    shadowElevation: Dp = 8.dp,
    layerAlpha: Float = PremiumSurfaces.layer1Alpha,
    showInnerGlow: Boolean = true,
    border: BorderStroke? = null,
    highlightCornerRadius: Dp = 26.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val cornerPx = with(LocalDensity.current) { highlightCornerRadius.toPx() }
    Surface(
        modifier = modifier.then(
            if (showInnerGlow) {
                Modifier.drawBehind {
                    drawRoundRect(
                        brush = PremiumGlow.cardInnerTop(size.height),
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        size = size,
                    )
                }
            } else {
                Modifier
            },
        ),
        shape = shape,
        color = PremiumSurfaces.layer1(layerAlpha),
        tonalElevation = 0.dp,
        shadowElevation = shadowElevation,
        border = border ?: PremiumSurfaces.panelBorder(),
    ) {
        Box(content = content)
    }
}

@Composable
fun PremiumGlassBar(
    modifier: Modifier = Modifier,
    shape: Shape = PremiumCardShape,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = PremiumSurfaces.bar(),
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, PremiumSurfaces.borderColor(0.12f)),
    ) {
        Box(content = content)
    }
}

fun supportsBackdropBlur(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
