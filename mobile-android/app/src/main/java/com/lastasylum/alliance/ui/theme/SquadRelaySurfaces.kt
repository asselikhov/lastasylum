package com.lastasylum.alliance.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Единые полупрозрачные панели поверх [AtmosphericBackground]:
 * один базовый тон, согласованные альфы — без резких скачков между блоками.
 */
object SquadRelaySurfaces {
    /** База «стекла» (близко к void, не к яркому M3 surface). */
    val glassBase = Color(0xFF0A0F1A)

    const val subtleAlpha = 0.40f
    const val panelAlpha = 0.58f
    const val barAlpha = 0.68f
    const val dialogAlpha = 0.90f
    const val borderAlpha = 0.16f

    @Composable
    @ReadOnlyComposable
    fun subtleColor(alpha: Float = subtleAlpha): Color = glassBase.copy(alpha = alpha)

    @Composable
    @ReadOnlyComposable
    fun panelColor(alpha: Float = panelAlpha): Color = glassBase.copy(alpha = alpha)

    @Composable
    @ReadOnlyComposable
    fun barColor(alpha: Float = barAlpha): Color = glassBase.copy(alpha = alpha)

    @Composable
    @ReadOnlyComposable
    fun dialogColor(alpha: Float = dialogAlpha): Color = glassBase.copy(alpha = alpha)

    @Composable
    @ReadOnlyComposable
    fun borderColor(alpha: Float = borderAlpha): Color =
        MaterialTheme.colorScheme.outline.copy(alpha = alpha)

    @Composable
    @ReadOnlyComposable
    fun panelBorder(width: Dp = 1.dp, alpha: Float = borderAlpha): BorderStroke =
        BorderStroke(width, borderColor(alpha))

    @Composable
    fun cardColors(alpha: Float = panelAlpha) = CardDefaults.cardColors(
        containerColor = panelColor(alpha),
    )
}
