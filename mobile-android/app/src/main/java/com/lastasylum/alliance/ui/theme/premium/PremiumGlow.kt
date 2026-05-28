package com.lastasylum.alliance.ui.theme.premium

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object PremiumGlow {
    fun ambientPurple(center: Offset, radius: Float): Brush =
        Brush.radialGradient(
            colors = listOf(PremiumColors.glowPurple, Color.Transparent),
            center = center,
            radius = radius,
        )

    fun ambientCyan(center: Offset, radius: Float): Brush =
        Brush.radialGradient(
            colors = listOf(PremiumColors.glowCyan, Color.Transparent),
            center = center,
            radius = radius,
        )

    fun cardInnerTop(height: Float): Brush =
        Brush.verticalGradient(
            colors = listOf(PremiumColors.innerHighlight, Color.Transparent),
            startY = 0f,
            endY = height * 0.22f,
        )

    fun pollProgress(): Brush =
        Brush.horizontalGradient(
            colors = listOf(PremiumColors.pollGradientStart, PremiumColors.pollGradientEnd),
        )
}
