package com.lastasylum.alliance.ui.components.team

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.ui.chat.ChatSenderAvatar
import com.lastasylum.alliance.ui.util.telegramAvatarUrl

@Composable
fun ForumTopicFeedCard(
    topic: TeamForumTopicDto,
    listIndex: Int,
    messageMeta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    displayUnreadCount: Int? = null,
    menu: @Composable () -> Unit = {},
) {
    val accent = ForumTopicCardTokens.accentForIndex(listIndex)
    val unreadCount = displayUnreadCount ?: topic.unreadCount
    val hasUnread = unreadCount > 0
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) ForumTopicCardTokens.pressScale else 1f,
        animationSpec = ForumTopicCardTokens.pressAnimSpec,
        label = "forumCardScale",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) ForumTopicCardTokens.elevationPressed else ForumTopicCardTokens.elevationRest,
        animationSpec = ForumTopicCardTokens.pressElevationAnimSpec,
        label = "forumCardElevation",
    )
    val cardShape = RoundedCornerShape(ForumTopicCardTokens.cardRadius)
    val metaDesc = buildString {
        append(topic.title)
        if (topic.messageCount > 0) append(", ${topic.messageCount} сообщений")
        if (hasUnread) append(", непрочитано: $unreadCount")
        if (messageMeta.isNotBlank()) append(", $messageMeta")
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .semantics { contentDescription = metaDesc },
        shape = cardShape,
        color = Color.Transparent,
        shadowElevation = elevation,
        interactionSource = interactionSource,
    ) {
        ForumTopicCardBackground(
            accent = accent,
            hasUnread = hasUnread,
            shape = cardShape,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = ForumTopicCardTokens.cardPaddingH,
                        vertical = ForumTopicCardTokens.cardPaddingV,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ForumTopicCardTokens.rowGap),
            ) {
                ForumTopicAvatar(
                    topic = topic,
                    accent = accent,
                    hasUnread = hasUnread,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ForumTopicCardTokens.titleMetaGap),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = topic.title,
                            style = ForumTopicCardTokens.titleStyle.copy(
                                color = ForumTopicCardTokens.titleColor(),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        menu()
                    }
                    ForumTopicMetaChipsRow(
                        messageCount = topic.messageCount,
                        activityLabel = messageMeta,
                        unreadCount = unreadCount,
                    )
                }
            }
        }
    }
}

@Composable
fun ForumTopicGhostIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.MoreVert,
    contentDescription: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val bg by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = ForumTopicCardTokens.pressAnimSpec,
        label = "ghostBtnBg",
    )
    val bgColor = androidx.compose.ui.graphics.lerp(
        ForumTopicCardTokens.ghostBgRest,
        ForumTopicCardTokens.ghostBgPressed,
        bg,
    )
    Surface(
        onClick = onClick,
        modifier = modifier.size(ForumTopicCardTokens.ghostButtonSize),
        shape = CircleShape,
        color = bgColor,
        interactionSource = interactionSource,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(ForumTopicCardTokens.ghostIconSize),
                tint = Color.White.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun ForumTopicCardBackground(
    accent: ForumTopicCardTokens.Accent,
    hasUnread: Boolean,
    shape: RoundedCornerShape,
    content: @Composable () -> Unit,
) {
    val shapeRadius = ForumTopicCardTokens.cardRadius
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ForumTopicCardTokens.cardBaseTop.copy(alpha = 0.84f),
                            ForumTopicCardTokens.cardBaseBottom.copy(alpha = 0.78f),
                        ),
                    ),
                )
                if (hasUnread) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accent.primary.copy(alpha = 0.14f),
                                accent.secondary.copy(alpha = 0.04f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.12f, size.height * 0.2f),
                            radius = size.maxDimension * 0.55f,
                        ),
                        radius = size.maxDimension * 0.55f,
                        center = Offset(size.width * 0.12f, size.height * 0.2f),
                    )
                }
                drawLine(
                    color = ForumTopicCardTokens.topHighlight,
                    start = Offset(shapeRadius.toPx() * 0.5f, 1f),
                    end = Offset(size.width - shapeRadius.toPx() * 0.5f, 1f),
                    strokeWidth = 1f,
                )
            },
        content = { content() },
    )
}

@Composable
private fun ForumTopicAvatar(
    topic: TeamForumTopicDto,
    accent: ForumTopicCardTokens.Accent,
    hasUnread: Boolean,
) {
    val creatorAvatarUrl = telegramAvatarUrl(topic.createdByTelegramUsername)
    Box {
        if (creatorAvatarUrl != null) {
            ChatSenderAvatar(
                telegramUrl = creatorAvatarUrl,
                size = ForumTopicCardTokens.avatarSize,
                fallbackName = topic.title,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(ForumTopicCardTokens.avatarSize)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                accent.primary.copy(alpha = 0.85f),
                                accent.secondary.copy(alpha = 0.65f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Forum,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (hasUnread) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(ForumTopicCardTokens.unreadDotSize)
                    .clip(CircleShape)
                    .background(ForumTopicCardTokens.unreadDotColor),
            )
        }
    }
}

@Composable
private fun ForumTopicMetaChip(
    label: String,
    leadingIcon: ImageVector?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(ForumTopicCardTokens.chipRadius),
        color = ForumTopicCardTokens.chipFill,
        border = BorderStroke(0.5.dp, ForumTopicCardTokens.chipBorder),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ForumTopicCardTokens.chipPaddingH,
                vertical = ForumTopicCardTokens.chipPaddingV,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.White.copy(alpha = 0.55f),
                )
            }
            Text(
                text = label,
                style = ForumTopicCardTokens.chipStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ForumTopicMetaChipsRow(
    messageCount: Int,
    activityLabel: String,
    unreadCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ForumTopicCardTokens.chipGap),
    ) {
        if (messageCount > 0) {
            ForumTopicMetaChip(
                label = stringResource(R.string.team_forum_chip_messages, messageCount),
                leadingIcon = Icons.Outlined.ChatBubbleOutline,
            )
        } else {
            ForumTopicMetaChip(
                label = stringResource(R.string.team_forum_no_messages),
                leadingIcon = null,
            )
        }
        val activity = activityLabel.trim()
        if (activity.isNotBlank() && activity != "—") {
            ForumTopicMetaChip(
                label = activity,
                leadingIcon = Icons.Outlined.AccessTime,
            )
        }
        if (unreadCount > 0) {
            ForumTopicMetaChip(
                label = stringResource(
                    R.string.team_forum_chip_unread,
                    if (unreadCount > 99) 99 else unreadCount,
                ),
                leadingIcon = Icons.Outlined.MarkChatUnread,
            )
        }
    }
}
