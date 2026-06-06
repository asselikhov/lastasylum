package com.lastasylum.alliance.ui.components.team

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
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
import com.lastasylum.alliance.ui.chat.pinnedPreviewLabel
import com.lastasylum.alliance.ui.theme.premium.PremiumColors
import com.lastasylum.alliance.ui.util.sanitizePublicDisplayName

@Composable
fun ForumTopicFeedCard(
    topic: TeamForumTopicDto,
    listIndex: Int,
    messageMeta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    displayUnreadCount: Int? = null,
    animationTier: FeedAnimationTier = FeedAnimationTier.Off,
    emberBoost: Float = 1f,
    menu: @Composable () -> Unit = {},
) {
    val accent = ForumTopicCardTokens.accentForIndex(listIndex)
    val badgeUnread = (displayUnreadCount ?: topic.unreadCount).coerceAtLeast(0)
    val activityLevel = ForumTopicCardTokens.activityLevel(badgeUnread, topic.messageCount)
    val hasUnread = badgeUnread > 0
    val pinPreview = topic.pinnedMessage
    val metaDesc = remember(topic.id, topic.title, topic.messageCount, badgeUnread, messageMeta) {
        buildString {
            append(topic.title)
            if (topic.messageCount > 0) append(", ${topic.messageCount} сообщений")
            if (hasUnread) append(", непрочитано: $badgeUnread")
            if (messageMeta.isNotBlank()) append(", $messageMeta")
        }
    }
    val metaLine = buildForumTopicMetaLine(
        messageCount = topic.messageCount,
        timeMeta = messageMeta,
        unreadCount = badgeUnread,
    )
    val titleStyle = ForumTopicCardTokens.titleStyle

    ForumTopicAnimatedShell(
        onClick = onClick,
        accent = accent,
        activityLevel = activityLevel,
        animationTier = animationTier,
        emberBoost = emberBoost,
        modifier = modifier.semantics { contentDescription = metaDesc },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ForumTopicCardTokens.cardContentHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ForumTopicCardTokens.rowGap),
        ) {
            ForumTopicCompactAvatar(
                topic = topic,
                accent = accent,
                activityLevel = activityLevel,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(ForumTopicCardTokens.cardContentHeight),
                verticalArrangement = Arrangement.spacedBy(ForumTopicCardTokens.textBlockGap),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ForumTopicCardTokens.titleLineHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = topic.title,
                        style = titleStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .width(ForumTopicCardTokens.badgeSlotWidth)
                            .height(ForumTopicCardTokens.titleLineHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (hasUnread) {
                            ForumTopicUnreadBadge(count = badgeUnread)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(ForumTopicCardTokens.actionsSlotWidth)
                            .height(ForumTopicCardTokens.titleLineHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        menu()
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ForumTopicCardTokens.subtitleLineHeight),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (pinPreview != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    end = ForumTopicCardTokens.subtitleEndInset(hasUnread),
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PushPin,
                                contentDescription = stringResource(R.string.forum_topic_pinned_preview),
                                modifier = Modifier.size(12.dp),
                                tint = PremiumColors.accentCyan.copy(alpha = 0.85f),
                            )
                            Text(
                                text = pinnedPreviewLabel(pinPreview),
                                style = ForumTopicCardTokens.metaStyle,
                                color = ForumTopicCardTokens.metaIcon,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ForumTopicCardTokens.metaLineHeight),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = metaLine,
                        style = ForumTopicCardTokens.metaStyle,
                        color = ForumTopicCardTokens.metaText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun buildForumTopicMetaLine(
    messageCount: Int,
    timeMeta: String,
    unreadCount: Int,
): String {
    val msgPart = if (messageCount > 0) {
        stringResource(R.string.team_forum_messages_pill, messageCount)
    } else {
        stringResource(R.string.team_forum_no_messages)
    }
    val timePart = timeMeta.trim().takeIf { it.isNotBlank() && it != "—" }.orEmpty()
    val unreadPart = if (unreadCount > 0) {
        stringResource(R.string.team_forum_chip_unread, unreadCount)
    } else {
        null
    }
    return listOfNotNull(msgPart, timePart.takeIf { it.isNotEmpty() }, unreadPart)
        .joinToString(" · ")
}

@Composable
fun ForumTopicUnreadBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val label = if (count > 99) "99+" else count.toString()
    Surface(
        modifier = modifier.widthIn(min = 20.dp),
        shape = RoundedCornerShape(8.dp),
        color = PremiumColors.accentCyan.copy(alpha = 0.16f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            PremiumColors.accentCyan.copy(alpha = 0.5f),
        ),
    ) {
        Text(
            text = label,
            style = ForumTopicCardTokens.metaStyle.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = PremiumColors.accentCyanBright,
            ),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1,
        )
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
    val fillAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.48f else 0.38f,
        animationSpec = ForumTopicCardTokens.pressAnimSpec,
        label = "forumMenuFill",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.size(ForumTopicCardTokens.ghostButtonSize),
        shape = CircleShape,
        color = ForumTopicCardTokens.glassBottom.copy(alpha = fillAlpha),
        border = androidx.compose.foundation.BorderStroke(
            0.75.dp,
            PremiumColors.accentCyan.copy(alpha = if (pressed) 0.26f else 0.18f),
        ),
        interactionSource = interactionSource,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(ForumTopicCardTokens.ghostIconSize),
                tint = Color(0xFFD8E8FF).copy(alpha = if (pressed) 0.88f else 0.72f),
            )
        }
    }
}

@Composable
private fun ForumTopicCompactAvatar(
    topic: TeamForumTopicDto,
    accent: ForumTopicCardTokens.Accent,
    activityLevel: ForumTopicCardTokens.ActivityLevel,
) {
    val creatorAvatarUrl = topic.createdByAvatarRelativeUrl?.trim()?.takeIf { it.isNotEmpty() }
    val showLastSenderAvatar = topic.messageCount > 0
    val avatarUrl = if (showLastSenderAvatar) {
        topic.lastMessageSenderAvatarRelativeUrl?.trim()?.takeIf { it.isNotEmpty() }
    } else {
        creatorAvatarUrl
    }
    val avatarFallbackName = if (showLastSenderAvatar) {
        topic.lastMessageSenderUsername?.trim()?.takeIf { it.isNotEmpty() }?.let {
            sanitizePublicDisplayName(it, fallback = topic.title)
        } ?: topic.title
    } else {
        topic.title
    }
    val ringAlpha = when (activityLevel) {
        ForumTopicCardTokens.ActivityLevel.Hot -> 0.88f
        ForumTopicCardTokens.ActivityLevel.Warm -> 0.62f
        ForumTopicCardTokens.ActivityLevel.Calm -> 0.42f
    }
    Box(
        modifier = Modifier.size(ForumTopicCardTokens.avatarOuter),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(ForumTopicCardTokens.avatarOuter)
                .clip(CircleShape)
                .border(
                    ForumTopicCardTokens.avatarRingWidth,
                    Brush.sweepGradient(
                        colors = listOf(
                            accent.primary.copy(alpha = ringAlpha),
                            PremiumColors.accentCyan.copy(alpha = ringAlpha * 0.85f),
                            accent.secondary.copy(alpha = ringAlpha * 0.7f),
                            accent.primary.copy(alpha = ringAlpha),
                        ),
                    ),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (showLastSenderAvatar || avatarUrl != null) {
                ChatSenderAvatar(
                    avatarRelativeUrl = avatarUrl,
                    size = ForumTopicCardTokens.avatarInner,
                    fallbackName = avatarFallbackName,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(ForumTopicCardTokens.avatarInner)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    accent.primary.copy(alpha = 0.9f),
                                    accent.secondary.copy(alpha = 0.7f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Forum,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.94f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
