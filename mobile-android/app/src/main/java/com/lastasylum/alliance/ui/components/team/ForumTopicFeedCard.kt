package com.lastasylum.alliance.ui.components.team

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Forum
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
    menu: @Composable () -> Unit = {},
) {
    val accent = ForumTopicCardTokens.accentForIndex(listIndex)
    val unreadCount = displayUnreadCount ?: topic.unreadCount
    val hasUnread = unreadCount > 0
    val activityLevel = ForumTopicCardTokens.activityLevel(unreadCount, topic.messageCount)
    val metaDesc = remember(topic.id, topic.title, topic.messageCount, unreadCount, messageMeta) {
        buildString {
            append(topic.title)
            if (topic.messageCount > 0) append(", ${topic.messageCount} сообщений")
            if (hasUnread) append(", непрочитано: $unreadCount")
            if (messageMeta.isNotBlank()) append(", $messageMeta")
        }
    }

    ForumTopicTacticalCardShell(
        onClick = onClick,
        accent = accent,
        activityLevel = activityLevel,
        modifier = modifier.semantics { contentDescription = metaDesc },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ForumTopicCardTokens.rowGap),
        ) {
            ForumTopicTacticalAvatar(
                topic = topic,
                accent = accent,
                activityLevel = activityLevel,
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
                    if (activityLevel != ForumTopicCardTokens.ActivityLevel.Calm) {
                        ForumTopicActivityPulseDot(
                            accent = accent,
                            hot = activityLevel == ForumTopicCardTokens.ActivityLevel.Hot,
                        )
                    }
                    Text(
                        text = topic.title,
                        style = ForumTopicCardTokens.titleStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    menu()
                }
                ForumTopicTacticalMetaRow(
                    messageCount = topic.messageCount,
                    activityLabel = messageMeta,
                    unreadCount = unreadCount,
                    activityLevel = activityLevel,
                )
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
    val glow by animateFloatAsState(
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
                            PremiumColors.accentCyan.copy(alpha = 0.18f * glow),
                            Color.Transparent,
                        ),
                        radius = size.minDimension * 0.85f,
                    ),
                    radius = size.minDimension * 0.55f,
                    center = center,
                )
            },
        shape = CircleShape,
        color = Color(0xFF152033).copy(alpha = 0.55f + glow * 0.12f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.linearGradient(
                colors = listOf(
                    PremiumColors.accentCyan.copy(alpha = 0.28f * glow),
                    PremiumColors.accentPurple.copy(alpha = 0.18f * glow),
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
private fun ForumTopicActivityPulseDot(
    accent: ForumTopicCardTokens.Accent,
    hot: Boolean,
) {
    val infinite = rememberInfiniteTransition(label = "forumTopicPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (hot) 900 else 1_400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "forumTopicPulseAlpha",
    )
    Box(
        modifier = Modifier
            .size(ForumTopicCardTokens.activityDotSize)
            .drawBehind {
                drawCircle(
                    color = accent.primary.copy(alpha = if (hot) 0.35f * pulse else 0.18f * pulse),
                    radius = size.minDimension * 0.95f,
                )
            }
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        accent.primary.copy(alpha = 0.85f + pulse * 0.15f),
                        accent.secondary.copy(alpha = 0.55f),
                    ),
                ),
            ),
    )
}

@Composable
private fun ForumTopicTacticalAvatar(
    topic: TeamForumTopicDto,
    accent: ForumTopicCardTokens.Accent,
    activityLevel: ForumTopicCardTokens.ActivityLevel,
) {
    val creatorAvatarUrl = telegramAvatarUrl(topic.createdByTelegramUsername)
    val ringAlpha = when (activityLevel) {
        ForumTopicCardTokens.ActivityLevel.Hot -> 0.92f
        ForumTopicCardTokens.ActivityLevel.Warm -> 0.72f
        ForumTopicCardTokens.ActivityLevel.Calm -> 0.48f
    }
    Box(
        modifier = Modifier
            .size(ForumTopicCardTokens.avatarOuter)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.primary.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                        radius = size.minDimension * 0.72f,
                    ),
                    radius = size.minDimension * 0.72f,
                    center = center,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .border(
                    ForumTopicCardTokens.avatarRingWidth,
                    Brush.sweepGradient(
                        colors = listOf(
                            accent.primary.copy(alpha = ringAlpha),
                            PremiumColors.accentCyan.copy(alpha = ringAlpha * 0.85f),
                            accent.secondary.copy(alpha = ringAlpha * 0.75f),
                            accent.primary.copy(alpha = ringAlpha),
                        ),
                    ),
                    CircleShape,
                ),
        )
        if (creatorAvatarUrl != null) {
            ChatSenderAvatar(
                telegramUrl = creatorAvatarUrl,
                size = ForumTopicCardTokens.avatarInner,
                fallbackName = topic.title,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(ForumTopicCardTokens.avatarInner)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                accent.primary.copy(alpha = 0.92f),
                                accent.secondary.copy(alpha = 0.72f),
                                Color(0xFF0D1524),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Forum,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.94f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ForumTopicTacticalMetaRow(
    messageCount: Int,
    activityLabel: String,
    unreadCount: Int,
    activityLevel: ForumTopicCardTokens.ActivityLevel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ForumTopicCardTokens.chipGap),
    ) {
        if (messageCount > 0) {
            ForumTopicTacticalChip(
                label = stringResource(R.string.team_forum_chip_messages, messageCount),
                leadingIcon = Icons.Outlined.ChatBubbleOutline,
                hot = activityLevel == ForumTopicCardTokens.ActivityLevel.Hot,
            )
        } else {
            ForumTopicTacticalChip(
                label = stringResource(R.string.team_forum_no_messages),
                leadingIcon = null,
            )
        }
        val activity = activityLabel.trim()
        if (activity.isNotBlank() && activity != "—") {
            ForumTopicTacticalChip(
                label = activity,
                leadingIcon = Icons.Outlined.AccessTime,
            )
        }
        if (unreadCount > 0) {
            ForumTopicTacticalChip(
                label = stringResource(R.string.team_forum_chip_unread, unreadCount),
                hot = true,
            )
        } else if (activityLevel == ForumTopicCardTokens.ActivityLevel.Warm) {
            ForumTopicTacticalChip(
                label = stringResource(R.string.team_forum_topic_active),
                hot = true,
            )
        }
    }
}

@Composable
private fun ForumTopicTacticalChip(
    label: String,
    leadingIcon: ImageVector? = null,
    hot: Boolean = false,
) {
    val borderColor = if (hot) ForumTopicCardTokens.chipBorderHot else ForumTopicCardTokens.chipBorder
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(ForumTopicCardTokens.chipRadius),
        color = ForumTopicCardTokens.chipFill,
        border = androidx.compose.foundation.BorderStroke(0.75.dp, borderColor),
    ) {
        Row(
            Modifier.padding(
                horizontal = ForumTopicCardTokens.chipPaddingH,
                vertical = ForumTopicCardTokens.chipPaddingV,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = if (hot) PremiumColors.accentCyan.copy(alpha = 0.85f) else ForumTopicCardTokens.metaIcon,
                )
            }
            Text(
                text = label,
                style = ForumTopicCardTokens.metaStyle,
                color = ForumTopicCardTokens.chipTextColor(hot),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
