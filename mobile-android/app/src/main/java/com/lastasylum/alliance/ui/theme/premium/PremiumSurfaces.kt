package com.lastasylum.alliance.ui.theme.premium

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object PremiumSurfaces {
    const val layer1Alpha = 0.52f
    /** Feed / profile list cards — opaque enough to avoid “fog inside” the card. */
    const val listCardAlpha = 0.90f
    const val layer2Alpha = 0.68f
    const val barAlpha = 0.76f
    const val dialogAlpha = 0.92f
    const val borderAlpha = 0.14f

    @Composable
    @ReadOnlyComposable
    fun layer1(alpha: Float = layer1Alpha): Color = PremiumColors.glassLayer1.copy(alpha = alpha)

    @Composable
    @ReadOnlyComposable
    fun layer2(alpha: Float = layer2Alpha): Color = PremiumColors.glassLayer2.copy(alpha = alpha)

    @Composable
    @ReadOnlyComposable
    fun bar(alpha: Float = barAlpha): Color = PremiumColors.glassLayer2.copy(alpha = alpha)

    @Composable
    @ReadOnlyComposable
    fun dialog(alpha: Float = dialogAlpha): Color = PremiumColors.glassLayer2.copy(alpha = alpha)

    @Composable
    @ReadOnlyComposable
    fun borderColor(alpha: Float = borderAlpha): Color =
        MaterialTheme.colorScheme.outline.copy(alpha = alpha)

    @Composable
    @ReadOnlyComposable
    fun accentBorder(width: Dp = 1.dp): BorderStroke =
        BorderStroke(width, PremiumColors.borderAccent)

    @Composable
    @ReadOnlyComposable
    fun panelBorder(width: Dp = 1.dp, alpha: Float = borderAlpha): BorderStroke =
        BorderStroke(width, borderColor(alpha))

    /** Top-lit glass edge for cards (no interior overlay). */
    @Composable
    @ReadOnlyComposable
    fun cardBorder(width: Dp = 1.dp): BorderStroke = BorderStroke(
        width = width,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.22f),
                borderColor(0.10f),
                Color.White.copy(alpha = 0.05f),
            ),
        ),
    )

    @Composable
    fun cardColors(alpha: Float = layer1Alpha) = CardDefaults.cardColors(
        containerColor = layer1(alpha),
    )
}
