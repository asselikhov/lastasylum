package com.lastasylum.alliance.ui.components.team

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import com.lastasylum.alliance.ui.util.telegramAvatarUrl

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

    ForumTopicAnimatedShell(
        onClick = onClick,
        accent = accent,
        activityLevel = activityLevel,
        animationTier = animationTier,
        emberBoost = emberBoost,
        modifier = modifier.semantics { contentDescription = metaDesc },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(ForumTopicCardTokens.rowGap),
        ) {
            ForumTopicCompactAvatar(
                topic = topic,
                accent = accent,
                activityLevel = activityLevel,
                showPulse = animationTier == FeedAnimationTier.Full &&
                    activityLevel == ForumTopicCardTokens.ActivityLevel.Hot,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ForumTopicCardTokens.titleMetaGap),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = topic.title,
                        style = ForumTopicCardTokens.titleStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (hasUnread) {
                        ForumTopicUnreadBadge(count = badgeUnread)
                    }
                    menu()
                }
                if (topic.pinnedMessageId != null && topic.pinnedMessage != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                            text = pinnedPreviewLabel(topic.pinnedMessage),
                            style = ForumTopicCardTokens.metaStyle,
                            color = ForumTopicCardTokens.metaIcon,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
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
    val glow by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 1f else 0.55f,
        animationSpec = ForumTopicCardTokens.pressAnimSpec,
        label = "forumMenuGlow",
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(ForumTopicCardTokens.ghostButtonSize)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PremiumColors.accentCyan.copy(alpha = 0.14f * glow),
                            Color.Transparent,
                        ),
                        radius = size.minDimension * 0.85f,
                    ),
                    radius = size.minDimension * 0.55f,
                    center = center,
                )
            },
        shape = CircleShape,
        color = Color(0xFF152033).copy(alpha = 0.5f + glow * 0.1f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.linearGradient(
                colors = listOf(
                    PremiumColors.accentCyan.copy(alpha = 0.24f * glow),
                    PremiumColors.accentPurple.copy(alpha = 0.14f * glow),
                ),
            ),
        ),
        interactionSource = interactionSource,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(ForumTopicCardTokens.ghostIconSize),
                tint = Color(0xFFD8E8FF).copy(alpha = 0.72f + glow * 0.2f),
            )
        }
    }
}

@Composable
private fun ForumTopicCompactAvatar(
    topic: TeamForumTopicDto,
    accent: ForumTopicCardTokens.Accent,
    activityLevel: ForumTopicCardTokens.ActivityLevel,
    showPulse: Boolean,
) {
    val creatorAvatarUrl = telegramAvatarUrl(topic.createdByTelegramUsername)
    val showLastSenderAvatar = topic.messageCount > 0
    val avatarUrl = if (showLastSenderAvatar) {
        telegramAvatarUrl(topic.lastMessageSenderTelegramUsername)
    } else {
        creatorAvatarUrl
    }
    val avatarFallbackName = if (showLastSenderAvatar) {
        topic.lastMessageSenderUsername?.trim()?.takeIf { it.isNotEmpty() } ?: topic.title
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
        if (showPulse) {
            ForumTopicActivityPulseDot(
                accent = accent,
                hot = true,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
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
                    telegramUrl = avatarUrl,
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

@Composable
private fun ForumTopicActivityPulseDot(
    accent: ForumTopicCardTokens.Accent,
    hot: Boolean,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "forumPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "forumPulseAlpha",
    )
    val firePalette = ForumTopicCardTokens.FirePalette
    val dotPrimary = if (hot) firePalette.orange else accent.primary
    Box(
        modifier = modifier
            .size(ForumTopicCardTokens.activityDotSize)
            .drawBehind {
                drawCircle(
                    color = firePalette.orange.copy(alpha = 0.25f * pulse),
                    radius = size.minDimension * 1.4f,
                )
            }
            .clip(CircleShape)
            .background(dotPrimary.copy(alpha = 0.85f + pulse * 0.15f)),
    )
}
