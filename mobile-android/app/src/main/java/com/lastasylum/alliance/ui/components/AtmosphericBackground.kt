package com.lastasylum.alliance.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithCache
import com.lastasylum.alliance.ui.theme.premium.PremiumColors
import com.lastasylum.alliance.ui.theme.premium.PremiumGlow

/**
 * AMOLED-friendly void with ambient radial glows and depth (API 28+ solid paints).
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
                            PremiumColors.voidTop,
                            PremiumColors.voidMid,
                            PremiumColors.voidMid,
                            PremiumColors.voidBottom,
                        ),
                        startY = 0f,
                        endY = h,
                    )
                    val accent1Center = Offset(w * 0.12f, h * 0.08f)
                    val accent1Radius = max * 0.78f
                    val accent1 = PremiumGlow.ambientPurple(accent1Center, accent1Radius)
                    val accent2Center = Offset(w * 0.9f, h * 0.92f)
                    val accent2Radius = max * 0.62f
                    val accent2 = PremiumGlow.ambientCyan(accent2Center, accent2Radius)
                    val accent3Center = Offset(w * 0.5f, h * 0.45f)
                    val accent3 = Brush.radialGradient(
                        colors = listOf(PremiumColors.glowBlue, Color.Transparent),
                        center = accent3Center,
                        radius = max * 0.55f,
                    )
                    val topFade = Brush.verticalGradient(
                        colors = listOf(
                            PremiumColors.voidTop.copy(alpha = 0.65f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = h * 0.18f,
                    )
                    onDrawBehind {
                        drawRect(brush = base)
                        drawCircle(brush = accent1, radius = accent1Radius, center = accent1Center)
                        drawCircle(brush = accent2, radius = accent2Radius, center = accent2Center)
                        drawCircle(brush = accent3, radius = max * 0.55f, center = accent3Center)
                        drawRect(brush = topFade)
                    }
                }
            },
    )
}
