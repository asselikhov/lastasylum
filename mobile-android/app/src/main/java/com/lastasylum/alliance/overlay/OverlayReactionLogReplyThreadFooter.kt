package com.lastasylum.alliance.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R

@Composable
fun OverlayReactionLogReplyThreadFooter(
    parentLogId: String,
    replyCount: Int,
    modifier: Modifier = Modifier,
    defaultExpanded: Boolean = false,
    incoming: Boolean = true,
    unreadHighlight: Boolean = false,
    expandedContent: @Composable () -> Unit,
) {
    if (replyCount <= 0) return
    var expanded by rememberSaveable(parentLogId, defaultExpanded) {
        mutableStateOf(defaultExpanded)
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "reply_thread_chevron",
    )
    val toggleCd = stringResource(
        if (expanded) {
            R.string.overlay_notifications_hide_replies
        } else {
            R.string.overlay_notifications_show_replies
        },
    )
    val countLabel = if (replyCount == 1) {
        stringResource(R.string.overlay_notifications_replies_count_one)
    } else {
        pluralStringResource(
            R.plurals.overlay_notifications_replies_count,
            replyCount,
            replyCount,
        )
    }
    val capsuleBorder = when {
        unreadHighlight && incoming -> ReactionLogCardTokens.borderUnread
        else -> Color(0x503D4A62)
    }
    val capsuleShape = RoundedCornerShape(12.dp)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            if (incoming) {
                                ReactionLogCardTokens.incomingGradientBottom.copy(alpha = 0.9f)
                            } else {
                                ReactionLogCardTokens.outgoingGradientBottom.copy(alpha = 0.9f)
                            },
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = toggleCd }
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = { expanded = !expanded },
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier
                    .offset(y = (-14).dp)
                    .clip(capsuleShape),
                shape = capsuleShape,
                color = Color(0xE6141C28),
                border = BorderStroke(1.dp, capsuleBorder),
                shadowElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(chevronRotation),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = countLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(tween(180)),
            exit = shrinkVertically() + fadeOut(tween(160)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 10.dp, end = 10.dp, bottom = 10.dp),
            ) {
                HorizontalDivider(
                    color = Color(0x20FFFFFF),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                expandedContent()
            }
        }
        if (!expanded) {
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
