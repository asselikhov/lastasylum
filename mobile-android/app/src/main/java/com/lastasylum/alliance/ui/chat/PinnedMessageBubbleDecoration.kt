package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R

private val PinnedAccentBarWidth = 3.dp
private val PinnedAccentBarInset = 4.dp
private val PinnedBubbleTintMix = 0.07f
private val PinnedBorderAlpha = 0.22f

fun pinnedMessageClusterTopExtra(): Dp = 6.dp

fun pinnedBubbleBackground(base: Color, primary: Color): Color =
    lerp(base, primary, PinnedBubbleTintMix)

fun pinnedBubbleBorder(
    default: BorderStroke,
    primary: Color,
    highlighted: Boolean,
): BorderStroke {
    if (highlighted) return default
    return BorderStroke(1.dp, primary.copy(alpha = PinnedBorderAlpha))
}

fun Modifier.pinnedMessageAccentBar(accentColor: Color): Modifier = drawBehind {
    val barWidth = PinnedAccentBarWidth.toPx()
    val inset = PinnedAccentBarInset.toPx()
    val barHeight = (size.height - inset * 2f).coerceAtLeast(barWidth)
    drawRoundRect(
        color = accentColor.copy(alpha = 0.88f),
        topLeft = Offset(0f, inset),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
    )
}

@Composable
fun PinnedMessageBubbleHeader(
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.PushPin,
            contentDescription = null,
            tint = accentColor.copy(alpha = 0.92f),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(R.string.chat_pinned_bar_cd),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = accentColor.copy(alpha = 0.88f),
        )
    }
}
