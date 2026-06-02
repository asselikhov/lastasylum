package com.lastasylum.alliance.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R

/**
 * CTA «ответить реакцией» на входящих карточках журнала — в тон зелёной палитры incoming-карт.
 */
@Composable
fun OverlayReactionReplyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    val accent = ReactionLogCardTokens.incomingRail
    val accentGlow = Color(0xFF6EE7B7)
    val shape = RoundedCornerShape(if (expanded) 14.dp else 11.dp)
    val label = stringResource(R.string.overlay_notifications_reply_action)
    val contentDesc = stringResource(R.string.overlay_notifications_reply_cd)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(90),
        label = "reply_cta_scale",
    )
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            accent.copy(alpha = 0.72f),
            accentGlow.copy(alpha = 0.38f),
        ),
    )
    val fillBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1F4D3F),
            Color(0xFF162E28),
            Color(0xFF121F1C),
        ),
    )
    val horizontalPadding = if (expanded) 16.dp else 10.dp
    val verticalPadding = if (expanded) 11.dp else 7.dp
    val iconSize = if (expanded) 18.dp else 15.dp
    val emojiSize = if (expanded) 16.dp else 14.dp

    Box(
        modifier = modifier
            .scale(scale)
            .semantics { contentDescription = contentDesc }
            .heightIn(min = if (expanded) 44.dp else 34.dp)
            .clip(shape)
            .background(fillBrush)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accentGlow.copy(alpha = 0.22f)),
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Reply,
                contentDescription = null,
                tint = accentGlow,
                modifier = Modifier.size(iconSize),
            )
            Spacer(modifier = Modifier.width(if (expanded) 8.dp else 5.dp))
            Text(
                text = label,
                style = if (expanded) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.labelMedium
                }.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFFE8FFF5),
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(if (expanded) 6.dp else 4.dp))
            Icon(
                imageVector = Icons.Outlined.EmojiEmotions,
                contentDescription = null,
                tint = accent.copy(alpha = 0.92f),
                modifier = Modifier.size(emojiSize),
            )
        }
    }
}
