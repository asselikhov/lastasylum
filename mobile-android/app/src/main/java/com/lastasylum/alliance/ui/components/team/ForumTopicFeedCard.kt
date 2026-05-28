package com.lastasylum.alliance.ui.components.team

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.lastasylum.alliance.ui.components.premium.FeedCardStatChip
import com.lastasylum.alliance.ui.components.premium.FeedCardUnreadCountPill
import com.lastasylum.alliance.ui.components.premium.PremiumFeedCardShell
import com.lastasylum.alliance.ui.theme.premium.PremiumTypography
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
    val metaDesc = buildString {
        append(topic.title)
        if (topic.messageCount > 0) append(", ${topic.messageCount} сообщений")
        if (hasUnread) append(", непрочитано: $unreadCount")
        if (messageMeta.isNotBlank()) append(", $messageMeta")
    }

    PremiumFeedCardShell(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = metaDesc },
        variant = FeedCardVariant.ForumTopic,
        isUnread = hasUnread,
        accentColor = accent.primary,
        contentPadding = PaddingValues(
            horizontal = FeedCardDesignTokens.contentPadding,
            vertical = 14.dp,
        ),
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ForumTopicAvatar(
                    topic = topic,
                    accent = accent,
                    hasUnread = hasUnread,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = topic.title,
                            style = PremiumTypography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
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
        },
    )
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
        animationSpec = TeamFeedCardTokens.pressAnimSpec,
        label = "ghostBtnBg",
    )
    val bgColor = androidx.compose.ui.graphics.lerp(
        Color.Transparent,
        Color.White.copy(alpha = 0.08f),
        bg,
    )
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = bgColor,
        interactionSource = interactionSource,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = Color.White.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun ForumTopicAvatar(
    topic: TeamForumTopicDto,
    accent: ForumTopicCardTokens.Accent,
    hasUnread: Boolean,
) {
    val creatorAvatarUrl = telegramAvatarUrl(topic.createdByTelegramUsername)
    val innerSize = if (hasUnread) 36.dp else FeedCardDesignTokens.avatarList
    Box(
        modifier = Modifier.size(FeedCardDesignTokens.avatarList),
        contentAlignment = Alignment.Center,
    ) {
        if (hasUnread) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .border(2.dp, FeedCardDesignTokens.unreadBorderColor, CircleShape),
            )
        }
        if (creatorAvatarUrl != null) {
            ChatSenderAvatar(
                telegramUrl = creatorAvatarUrl,
                size = innerSize,
                fallbackName = topic.title,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(innerSize)
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (messageCount > 0) {
            FeedCardStatChip(
                label = stringResource(R.string.team_forum_chip_messages, messageCount),
                leadingIcon = Icons.Outlined.ChatBubbleOutline,
            )
        } else {
            FeedCardStatChip(
                label = stringResource(R.string.team_forum_no_messages),
                leadingIcon = null,
            )
        }
        val activity = activityLabel.trim()
        if (activity.isNotBlank() && activity != "—") {
            FeedCardStatChip(
                label = activity,
                leadingIcon = Icons.Outlined.AccessTime,
            )
        }
        if (unreadCount > 0) {
            FeedCardUnreadCountPill(count = unreadCount)
        }
    }
}
