package com.lastasylum.alliance.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.components.premium.PremiumGlassSurface
import com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces

internal object ReactionLogCardTokens {
    val corner = 14.dp
    val railWidth = 3.dp
    val borderUnread = Color(0x664ADE80)
    val borderIncoming = Color(0x5534D399)
    val borderOutgoing = Color(0x5560A5FA)
    val incomingRail = Color(0xFF34D399)
    val outgoingRail = Color(0xFF60A5FA)
    val incomingGradientTop = Color(0x2834D399)
    val incomingGradientBottom = Color(0x0A0F1A14)
    val outgoingGradientTop = Color(0x2860A5FA)
    val outgoingGradientBottom = Color(0x0A101C2E)
    val incomingUnreadGlow = Color(0x1A34D399)
    val outgoingUnreadGlow = Color(0x1A60A5FA)
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
        targetValue = when {
            unreadHighlight && incoming -> ReactionLogCardTokens.borderUnread
            incoming -> ReactionLogCardTokens.borderIncoming
            else -> ReactionLogCardTokens.borderOutgoing
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
    val cardAlpha = (PremiumSurfaces.listCardAlpha + 0.04f).coerceIn(0.72f, 0.96f)
    val railWidthPx = with(LocalDensity.current) { ReactionLogCardTokens.railWidth.toPx() }
    val railAlpha = when {
        unreadHighlight -> 0.98f
        incoming -> 0.78f
        else -> 0.78f
    }
    PremiumGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = shape,
        shadowElevation = 4.dp,
        layerAlpha = cardAlpha,
        showInnerGlow = true,
        border = BorderStroke(1.dp, animatedBorderColor),
        highlightCornerRadius = ReactionLogCardTokens.corner,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .drawBehind {
                    drawRect(brush = gradientBrush, size = size)
                    if (unreadHighlight) {
                        val glow = if (incoming) {
                            ReactionLogCardTokens.incomingUnreadGlow
                        } else {
                            ReactionLogCardTokens.outgoingUnreadGlow
                        }
                        drawRect(color = glow, size = size)
                    }
                    val railColor = animatedRailColor.copy(alpha = railAlpha)
                    if (incoming) {
                        drawRect(
                            color = railColor,
                            topLeft = Offset.Zero,
                            size = Size(railWidthPx, size.height),
                        )
                    } else {
                        drawRect(
                            color = railColor,
                            topLeft = Offset(size.width - railWidthPx, 0f),
                            size = Size(railWidthPx, size.height),
                        )
                    }
                },
        ) {
            content()
        }
    }
}
