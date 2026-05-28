package com.lastasylum.alliance.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithCache
import com.lastasylum.alliance.ui.theme.SquadRelayAtmosphericSky
import com.lastasylum.alliance.ui.theme.SquadRelayBackground
import com.lastasylum.alliance.ui.theme.SquadRelayAtmosphericPurple
import com.lastasylum.alliance.ui.theme.SquadRelayVoidBottom
import com.lastasylum.alliance.ui.theme.SquadRelayVoidTop

/**
 * Layered void + radial accents (AAA shell). Solid paints only — works on minSdk 28.
 */
@Composable
fun AtmosphericBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) {
                    onDrawBehind { }
                } else {
                    val max = w.coerceAtLeast(h)
                    val base = Brush.verticalGradient(
                        colors = listOf(
                            SquadRelayVoidTop,
                            SquadRelayBackground,
                            SquadRelayBackground,
                            SquadRelayVoidBottom,
                        ),
                        startY = 0f,
                        endY = h,
                    )
                    val accent1Center = Offset(w * 0.1f, h * 0.12f)
                    val accent1Radius = max * 0.72f
                    val accent1 = Brush.radialGradient(
                        colors = listOf(
                            SquadRelayAtmosphericPurple.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                        center = accent1Center,
                        radius = accent1Radius,
                    )
                    val accent2Center = Offset(w * 0.88f, h * 0.88f)
                    val accent2Radius = max * 0.55f
                    val accent2 = Brush.radialGradient(
                        colors = listOf(
                            SquadRelayAtmosphericSky.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                        center = accent2Center,
                        radius = accent2Radius,
                    )
                    val topFade = Brush.verticalGradient(
                        colors = listOf(
                            SquadRelayVoidTop.copy(alpha = 0.55f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = h * 0.14f,
                    )
                    onDrawBehind {
                        drawRect(brush = base)
                        drawCircle(brush = accent1, radius = accent1Radius, center = accent1Center)
                        drawCircle(brush = accent2, radius = accent2Radius, center = accent2Center)
                        drawRect(brush = topFade)
                    }
                }
            },
    )
}
