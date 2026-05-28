package com.lastasylum.alliance.ui.components.premium

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.premium.PremiumColors
import com.lastasylum.alliance.ui.theme.premium.PremiumFabShape

@Composable
fun PremiumGradientFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(PremiumFabShape)
            .background(
                Brush.horizontalGradient(
                    listOf(PremiumColors.accentPurple, PremiumColors.accentCyan),
                ),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = PremiumColors.accentCyan),
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * Floating action button styled as an energy core — cyan → blue → purple glow with soft pulse.
 */
@Composable
fun PremiumGradientIconFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "energyCoreFab")
    val pulse by infinite.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "energyCorePulse",
    )
    val coreSize = 58.dp
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(coreSize + 28.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                PremiumColors.accentCyan.copy(alpha = 0.28f * pulse),
                                PremiumColors.accentPurple.copy(alpha = 0.14f * pulse),
                                Color.Transparent,
                            ),
                            radius = size.minDimension * 0.55f,
                        ),
                        radius = size.minDimension * 0.55f,
                        center = center,
                    )
                },
        )
        Box(
            modifier = Modifier
                .size(coreSize)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                PremiumColors.accentCyan.copy(alpha = 0.35f * pulse),
                                Color.Transparent,
                            ),
                            radius = size.minDimension * 0.72f,
                        ),
                        radius = size.minDimension * 0.72f,
                        center = center,
                    )
                }
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            PremiumColors.accentCyan.copy(alpha = 0.95f),
                            Color(0xFF4A7CFF),
                            PremiumColors.accentPurpleDeep,
                        ),
                    ),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Color.White.copy(alpha = 0.35f)),
                    onClick = onClick,
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
