package com.lastasylum.alliance.ui.components.premium

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.components.team.FeedCardDesignTokens
import com.lastasylum.alliance.ui.theme.premium.PremiumColors
import com.lastasylum.alliance.ui.theme.premium.PremiumGlow
import com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces

@Composable
fun PremiumProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    barHeight: Dp = 10.dp,
    minVisibleFraction: Float = FeedCardDesignTokens.minPollBarProgress,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val visible = if (clamped > 0f) clamped.coerceAtLeast(minVisibleFraction) else 0f
    val animated by animateFloatAsState(
        targetValue = visible,
        animationSpec = if (animate) tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing) else tween(0),
        label = "pollProgress",
    )
    Box(
        modifier = modifier
            .height(barHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(PremiumSurfaces.layer2(0.45f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(RoundedCornerShape(8.dp))
                .background(PremiumGlow.pollProgress())
                .drawBehind {
                    drawRoundRect(
                        color = PremiumColors.glowCyan.copy(alpha = 0.35f),
                        topLeft = Offset(size.width * 0.5f, 0f),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                    )
                },
        )
    }
}
