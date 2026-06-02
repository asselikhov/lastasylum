package com.lastasylum.alliance.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R

/**
 * Иконка «ответить реакцией» в строке карточки (слева от превью) или в детальном sheet.
 */
@Composable
fun OverlayReactionReplyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    incoming: Boolean = true,
    buttonSize: Dp = 34.dp,
    iconSize: Dp = 19.dp,
) {
    val accent = if (incoming) ReactionLogCardTokens.incomingRail else ReactionLogCardTokens.outgoingRail
    val accentGlow = if (incoming) Color(0xFF6EE7B7) else Color(0xFF93C5FD)
    val fillTop = accent.copy(alpha = if (incoming) 0.16f else 0.14f)
    val fillBottom = if (incoming) Color(0xFF141E1A) else Color(0xFF141A24)
    val shape = RoundedCornerShape(10.dp)
    val contentDesc = stringResource(R.string.overlay_notifications_reply_cd)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(90),
        label = "reply_icon_scale",
    )
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            accent.copy(alpha = if (incoming) 0.55f else 0.5f),
            accent.copy(alpha = 0.22f),
        ),
    )
    val fillBrush = Brush.verticalGradient(
        colors = listOf(fillTop, fillBottom),
    )

    Box(
        modifier = modifier
            .scale(scale)
            .semantics { contentDescription = contentDesc }
            .size(buttonSize)
            .clip(shape)
            .background(fillBrush)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accentGlow.copy(alpha = 0.18f)),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Reply,
            contentDescription = null,
            tint = accentGlow.copy(alpha = 0.96f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Крупнее вариант для sheet деталей реакции. */
@Composable
fun OverlayReactionReplyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    incoming: Boolean = true,
) {
    OverlayReactionReplyIconButton(
        onClick = onClick,
        modifier = modifier,
        incoming = incoming,
        buttonSize = if (expanded) 44.dp else 34.dp,
        iconSize = if (expanded) 22.dp else 19.dp,
    )
}
