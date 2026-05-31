package com.lastasylum.alliance.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.components.premium.PremiumGlassSurface
import com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces

internal object ReactionLogCardTokens {
    val corner = 14.dp
    val railWidth = 3.dp
    val borderUnread = Color(0x5538BDF8)
    val incomingRail = Color(0xFF38BDF8)
    val outgoingRail = Color(0xFF6B7A90)
    val incomingGradientTop = Color(0x18FFFFFF)
    val incomingGradientBottom = Color(0x060E1624)
    val outgoingGradientTop = Color(0x14FFFFFF)
    val outgoingGradientBottom = Color(0x060E1624)
    val presenceOffline = Color(0xFF667788)
    val presenceRing = Color(0xFF0E1624)
}

@Composable
fun OverlayReactionLogCard(
    incoming: Boolean,
    unreadHighlight: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(ReactionLogCardTokens.corner)
    val animatedBorderColor by animateColorAsState(
        targetValue = if (unreadHighlight && incoming) {
            ReactionLogCardTokens.borderUnread
        } else {
            Color(0x403D4A62)
        },
        animationSpec = tween(200),
        label = "reaction_log_border",
    )
    val animatedRailColor by animateColorAsState(
        targetValue = if (incoming) ReactionLogCardTokens.incomingRail else ReactionLogCardTokens.outgoingRail,
        animationSpec = tween(200),
        label = "reaction_log_rail",
    )
    val gradientBrush = Brush.verticalGradient(
        listOf(
            if (incoming) ReactionLogCardTokens.incomingGradientTop else ReactionLogCardTokens.outgoingGradientTop,
            if (incoming) ReactionLogCardTokens.incomingGradientBottom else ReactionLogCardTokens.outgoingGradientBottom,
        ),
    )
    PremiumGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        shadowElevation = 3.dp,
        layerAlpha = PremiumSurfaces.listCardAlpha,
        showInnerGlow = true,
        border = BorderStroke(1.dp, animatedBorderColor),
        highlightCornerRadius = ReactionLogCardTokens.corner,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(brush = gradientBrush, size = size)
                },
        ) {
            if (incoming) {
                Box(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.CenterStart)
                        .width(ReactionLogCardTokens.railWidth)
                        .fillMaxHeight()
                        .background(animatedRailColor.copy(alpha = if (unreadHighlight) 0.95f else 0.65f)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.CenterEnd)
                        .width(ReactionLogCardTokens.railWidth)
                        .fillMaxHeight()
                        .background(animatedRailColor.copy(alpha = 0.55f)),
                )
            }
            content()
        }
    }
}
