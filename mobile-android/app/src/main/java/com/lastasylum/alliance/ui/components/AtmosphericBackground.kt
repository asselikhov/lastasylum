package com.lastasylum.alliance.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
            .drawBehind {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@drawBehind
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            SquadRelayVoidTop,
                            SquadRelayBackground,
                            SquadRelayBackground,
                            SquadRelayVoidBottom,
                        ),
                        startY = 0f,
                        endY = h,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SquadRelayAtmosphericPurple.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.1f, h * 0.12f),
                        radius = w.coerceAtLeast(h) * 0.72f,
                    ),
                    radius = w.coerceAtLeast(h) * 0.72f,
                    center = Offset(w * 0.1f, h * 0.12f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SquadRelayAtmosphericSky.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.88f, h * 0.88f),
                        radius = w.coerceAtLeast(h) * 0.55f,
                    ),
                    radius = w.coerceAtLeast(h) * 0.55f,
                    center = Offset(w * 0.88f, h * 0.88f),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            SquadRelayVoidTop.copy(alpha = 0.55f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = h * 0.14f,
                    ),
                )
            },
    )
}
