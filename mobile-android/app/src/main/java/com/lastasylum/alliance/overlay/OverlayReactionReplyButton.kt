package com.lastasylum.alliance.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.vector.ImageVector
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
    buttonSize: Dp = 36.dp,
    iconSize: Dp = 22.dp,
) {
    val accent = if (incoming) ReactionLogCardTokens.incomingRail else ReactionLogCardTokens.outgoingRail
    val accentGlow = if (incoming) Color(0xFF6EE7B7) else Color(0xFF93C5FD)
    val shape = RoundedCornerShape(11.dp)
    val contentDesc = stringResource(R.string.overlay_notifications_reply_cd)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(90),
        label = "reply_icon_scale",
    )
    val fillBrush = Brush.verticalGradient(
        colors = listOf(
            accent.copy(alpha = if (incoming) 0.20f else 0.16f),
            accent.copy(alpha = if (incoming) 0.06f else 0.05f),
            Color.Transparent,
        ),
    )

    Box(
        modifier = modifier
            .scale(scale)
            .semantics { contentDescription = contentDesc }
            .size(buttonSize)
            .clip(shape)
            .background(fillBrush)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accentGlow.copy(alpha = 0.16f)),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        OverlayReactionReplyIcon3D(
            imageVector = Icons.AutoMirrored.Outlined.Reply,
            iconSize = iconSize,
            incoming = incoming,
        )
    }
}

@Composable
private fun OverlayReactionReplyIcon3D(
    imageVector: ImageVector,
    iconSize: Dp,
    incoming: Boolean,
) {
    val shadow = if (incoming) Color(0xFF052218) else Color(0xFF0A1628)
    val body = if (incoming) ReactionLogCardTokens.incomingRail else ReactionLogCardTokens.outgoingRail
    val highlight = if (incoming) Color(0xFFA7F3D0) else Color(0xFFBFDBFE)
    val canvas = iconSize + 4.dp
    Box(modifier = Modifier.size(canvas), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = shadow.copy(alpha = 0.85f),
            modifier = Modifier
                .size(iconSize)
                .offset(x = 1.4.dp, y = 2.dp),
        )
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = body,
            modifier = Modifier
                .size(iconSize)
                .offset(x = 0.25.dp, y = 0.5.dp),
        )
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = highlight.copy(alpha = 0.72f),
            modifier = Modifier
                .size(iconSize * 0.88f)
                .offset(x = (-0.6).dp, y = (-0.8).dp),
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
        buttonSize = if (expanded) 44.dp else 36.dp,
        iconSize = if (expanded) 24.dp else 22.dp,
    )
}
