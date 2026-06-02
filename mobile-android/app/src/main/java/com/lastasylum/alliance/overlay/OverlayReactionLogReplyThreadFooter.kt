package com.lastasylum.alliance.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces
import kotlin.math.min

private val CapsuleShape = RoundedCornerShape(12.dp)
private val ThreadPanelShape = RoundedCornerShape(
    topStart = 6.dp,
    topEnd = 6.dp,
    bottomStart = ReactionLogCardTokens.corner,
    bottomEnd = ReactionLogCardTokens.corner,
)
private val CapsuleOverlap = 14.dp
private val CapsuleReserve = 12.dp

/**
 * Обёртка для карточки с ответами: [cardContent] не меняет границ по сравнению с обычной карточкой;
 * капсула раскрытия сидит на нижнем контуре; ответы рисуются отдельной панелью снизу.
 */
@Composable
fun OverlayReactionLogReplyThreadFooter(
    parentLogId: String,
    replyCount: Int,
    modifier: Modifier = Modifier,
    defaultExpanded: Boolean = false,
    incoming: Boolean = true,
    unreadHighlight: Boolean = false,
    cardContent: @Composable () -> Unit,
    expandedContent: @Composable () -> Unit,
) {
    if (replyCount <= 0) {
        Box(modifier = modifier.fillMaxWidth()) {
            cardContent()
        }
        return
    }

    var expanded by rememberSaveable(parentLogId) { mutableStateOf(defaultExpanded) }
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
    val railColor = if (incoming) {
        ReactionLogCardTokens.incomingRail
    } else {
        ReactionLogCardTokens.outgoingRail
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(bottom = CapsuleReserve),
        ) {
            cardContent()
            ReplyThreadCapsule(
                expanded = expanded,
                chevronRotation = chevronRotation,
                countLabel = countLabel,
                toggleCd = toggleCd,
                capsuleBorder = capsuleBorder,
                onToggle = { expanded = !expanded },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = CapsuleOverlap),
            )
        }

        // No AnimatedVisibility: AndroidView previews detach/attach incorrectly during exit animation.
        if (expanded) {
            ReplyThreadExpandedPanel(
                incoming = incoming,
                unreadHighlight = unreadHighlight,
                railColor = railColor,
                content = expandedContent,
            )
        }
    }
}

@Composable
private fun ReplyThreadCapsule(
    expanded: Boolean,
    chevronRotation: Float,
    countLabel: String,
    toggleCd: String,
    capsuleBorder: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .semantics { contentDescription = toggleCd }
            .clip(CapsuleShape)
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onToggle,
            ),
        shape = CapsuleShape,
        color = Color(0xF0141C28),
        border = BorderStroke(1.dp, capsuleBorder),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
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

@Composable
private fun ReplyThreadExpandedPanel(
    incoming: Boolean,
    unreadHighlight: Boolean,
    railColor: Color,
    content: @Composable () -> Unit,
) {
    val panelAlpha = min(PremiumSurfaces.layer1Alpha + 0.10f, 0.62f)
    val panelBase = PremiumSurfaces.layer1(panelAlpha)
    val panelTintTop = if (incoming) {
        ReactionLogCardTokens.incomingGradientTop.copy(alpha = 0.14f)
    } else {
        ReactionLogCardTokens.outgoingGradientTop.copy(alpha = 0.10f)
    }
    val panelTintBottom = PremiumSurfaces.layer1(panelAlpha + 0.04f)
    val panelGradient = Brush.verticalGradient(
        colors = listOf(
            panelTintTop,
            panelTintBottom,
        ),
    )
    val borderStroke = when {
        unreadHighlight && incoming ->
            BorderStroke(1.dp, ReactionLogCardTokens.borderUnread.copy(alpha = 0.35f))
        else -> PremiumSurfaces.panelBorder(width = 1.dp, alpha = 0.10f)
    }
    val railWidth = ReactionLogCardTokens.railWidth
    val railWidthPx = with(LocalDensity.current) { railWidth.toPx() }
    val railAlpha = 0.52f
    val contentStartPad = if (incoming) 10.dp else 8.dp
    val contentEndPad = if (incoming) 8.dp else 10.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(top = 4.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = ThreadPanelShape,
            color = panelBase,
            shadowElevation = 0.dp,
            border = borderStroke,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .drawBehind {
                        drawRect(brush = panelGradient, size = size)
                        if (incoming) {
                            drawRect(
                                color = railColor.copy(alpha = railAlpha),
                                topLeft = Offset.Zero,
                                size = Size(railWidthPx, size.height),
                            )
                        } else {
                            drawRect(
                                color = railColor.copy(alpha = railAlpha),
                                topLeft = Offset(size.width - railWidthPx, 0f),
                                size = Size(railWidthPx, size.height),
                            )
                        }
                    }
                    .padding(
                        start = if (incoming) railWidth + contentStartPad else contentStartPad,
                        end = if (incoming) contentEndPad else railWidth + contentEndPad,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
            ) {
                content()
            }
        }
    }
}
