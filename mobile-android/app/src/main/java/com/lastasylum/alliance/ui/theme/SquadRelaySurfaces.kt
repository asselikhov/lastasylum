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
    /** База «стекла» — delegates to [com.lastasylum.alliance.ui.theme.premium.PremiumColors]. */
    val glassBase = com.lastasylum.alliance.ui.theme.premium.PremiumColors.glassLayer1

    const val subtleAlpha = 0.40f
    const val panelAlpha = com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces.layer1Alpha
    const val barAlpha = com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces.barAlpha
    const val dialogAlpha = com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces.dialogAlpha
    const val borderAlpha = com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces.borderAlpha

    @Composable
    @ReadOnlyComposable
    fun subtleColor(alpha: Float = subtleAlpha): Color =
        com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces.layer1(alpha)

    @Composable
    @ReadOnlyComposable
    fun panelColor(alpha: Float = panelAlpha): Color =
        com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces.layer1(alpha)

    @Composable
    @ReadOnlyComposable
    fun barColor(alpha: Float = barAlpha): Color =
        com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces.bar(alpha)

    @Composable
    @ReadOnlyComposable
    fun dialogColor(alpha: Float = dialogAlpha): Color =
        com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces.dialog(alpha)

    @Composable
    @ReadOnlyComposable
    fun borderColor(alpha: Float = borderAlpha): Color =
        com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces.borderColor(alpha)

    @Composable
    @ReadOnlyComposable
    fun panelBorder(width: Dp = 1.dp, alpha: Float = borderAlpha): BorderStroke =
        com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces.panelBorder(width, alpha)

    @Composable
    fun cardColors(alpha: Float = panelAlpha) =
        com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces.cardColors(alpha)
}
