package com.lastasylum.alliance.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionLogCluster
import com.lastasylum.alliance.data.chat.OverlayReactionLogEntry
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibility
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibilityPolicy
import com.lastasylum.alliance.ui.chat.ChatMessageReactionsRow
import com.lastasylum.alliance.ui.theme.premium.PremiumColors
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverlayReactionLogEntryRow(
    cluster: OverlayReactionLogCluster,
    selfUserId: String,
    unreadHighlight: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onQuickReply: (() -> Unit)?,
    onToggleEmojiReaction: ((String) -> Unit)?,
    compactLayout: Boolean,
    playAnimatedPreview: Boolean = true,
    isOnline: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val entry = cluster.representative
    val incoming = OverlayReactionLogVisibilityPolicy.isIncoming(entry, selfUserId)
    val scopeLabel = when (entry.visibility) {
        OverlayReactionLogVisibility.Personal ->
            stringResource(R.string.overlay_reaction_burst_caption_private)
        OverlayReactionLogVisibility.Broadcast ->
            stringResource(R.string.overlay_reaction_burst_caption_broadcast)
    }
    val scopeColor = when (entry.visibility) {
        OverlayReactionLogVisibility.Personal -> Color(0xFF9070B8)
        OverlayReactionLogVisibility.Broadcast -> Color(0xFF50B860)
    }
    val timeLine = formatOverlayReactionLogTimeLabel(entry.createdAt)

    val clickableModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier.combinedClickable(onClick = onClick)
    }

    OverlayReactionLogCard(
        incoming = incoming,
        unreadHighlight = unreadHighlight,
        modifier = modifier,
    ) {
        if (compactLayout) {
            CompactReactionLogRowContent(
                entry = entry,
                cluster = cluster,
                incoming = incoming,
                scopeLabel = scopeLabel,
                scopeColor = scopeColor,
                timeLine = timeLine,
                selfUserId = selfUserId,
                onQuickReply = onQuickReply,
                onToggleEmojiReaction = onToggleEmojiReaction,
                clickableModifier = clickableModifier,
                playAnimatedPreview = playAnimatedPreview,
                isOnline = isOnline,
            )
        } else {
            WideReactionLogRowContent(
                entry = entry,
                cluster = cluster,
                incoming = incoming,
                scopeLabel = scopeLabel,
                scopeColor = scopeColor,
                timeLine = timeLine,
                selfUserId = selfUserId,
                onQuickReply = onQuickReply,
                onToggleEmojiReaction = onToggleEmojiReaction,
                clickableModifier = clickableModifier,
                playAnimatedPreview = playAnimatedPreview,
                isOnline = isOnline,
            )
        }
    }
}

@Composable
private fun WideReactionLogRowContent(
    entry: OverlayReactionLogEntry,
    cluster: OverlayReactionLogCluster,
    incoming: Boolean,
    scopeLabel: String,
    scopeColor: Color,
    timeLine: String,
    selfUserId: String,
    onQuickReply: (() -> Unit)?,
    onToggleEmojiReaction: ((String) -> Unit)?,
    clickableModifier: Modifier,
    playAnimatedPreview: Boolean,
    isOnline: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SenderAvatarBlock(entry = entry, incoming = incoming, isOnline = isOnline)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            NarrativeClickableBlock(
                entry = entry,
                cluster = cluster,
                scopeLabel = scopeLabel,
                scopeColor = scopeColor,
                timeLine = timeLine,
                selfUserId = selfUserId,
                modifier = clickableModifier,
                alignTimeEnd = true,
            )
            AnimatedReactionsRow(
                reactions = entry.reactions,
                onToggleEmojiReaction = onToggleEmojiReaction,
            )
        }
        ReactionPreviewWithActions(
            cluster = cluster,
            entry = entry,
            incoming = incoming,
            onQuickReply = onQuickReply,
            playAnimatedPreview = playAnimatedPreview,
        )
    }
}

@Composable
private fun CompactReactionLogRowContent(
    entry: OverlayReactionLogEntry,
    cluster: OverlayReactionLogCluster,
    incoming: Boolean,
    scopeLabel: String,
    scopeColor: Color,
    timeLine: String,
    selfUserId: String,
    onQuickReply: (() -> Unit)?,
    onToggleEmojiReaction: ((String) -> Unit)?,
    clickableModifier: Modifier,
    playAnimatedPreview: Boolean,
    isOnline: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SenderAvatarBlock(entry = entry, incoming = incoming, isOnline = isOnline)
            Spacer(modifier = Modifier.width(10.dp))
            NarrativeClickableBlock(
                entry = entry,
                cluster = cluster,
                scopeLabel = scopeLabel,
                scopeColor = scopeColor,
                timeLine = timeLine,
                selfUserId = selfUserId,
                modifier = Modifier
                    .weight(1f)
                    .then(clickableModifier),
                alignTimeEnd = false,
            )
        }
        AnimatedReactionsRow(
            reactions = entry.reactions,
            onToggleEmojiReaction = onToggleEmojiReaction,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReactionPreviewWithActions(
                cluster = cluster,
                entry = entry,
                incoming = incoming,
                onQuickReply = onQuickReply,
                playAnimatedPreview = playAnimatedPreview,
            )
        }
    }
}

@Composable
private fun AnimatedReactionsRow(
    reactions: List<com.lastasylum.alliance.data.chat.ChatReaction>,
    onToggleEmojiReaction: ((String) -> Unit)?,
) {
    if (reactions.isEmpty()) return
    var bump by remember { mutableStateOf(false) }
    LaunchedEffect(reactions) {
        bump = true
        delay(120)
        bump = false
    }
    val scale by animateFloatAsState(
        targetValue = if (bump) 1.06f else 1f,
        animationSpec = tween(120),
        label = "reaction_row_scale",
    )
    ChatMessageReactionsRow(
        reactions = reactions,
        onReactionToggle = onToggleEmojiReaction,
        modifier = Modifier
            .padding(top = 4.dp)
            .scale(scale),
    )
}

@Composable
private fun ReactionPreviewWithActions(
    cluster: OverlayReactionLogCluster,
    entry: OverlayReactionLogEntry,
    incoming: Boolean,
    onQuickReply: (() -> Unit)?,
    playAnimatedPreview: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (cluster.mergeCount > 1) {
            OverlayReactionLogStackedPreview(
                cluster = cluster,
                playAnimatedPreview = playAnimatedPreview,
            )
        } else {
            OverlayReactionLogMiniPreview(
                reactionId = entry.reaction,
                visibility = entry.visibility,
                showLabel = false,
                playAnimatedPreview = playAnimatedPreview,
            )
        }
        if (incoming && onQuickReply != null) {
            IconButton(
                onClick = onQuickReply,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Reply,
                    contentDescription = stringResource(R.string.overlay_notifications_reply_cd),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun SenderAvatarBlock(
    entry: OverlayReactionLogEntry,
    incoming: Boolean,
    isOnline: Boolean,
) {
    Box(contentAlignment = Alignment.BottomEnd) {
        OverlayReactionLogAvatarWithPresence(
            userId = entry.senderUserId,
            username = entry.senderUsername,
            sizeDp = 40,
            isOnline = isOnline,
        )
        Icon(
            imageVector = if (incoming) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .background(Color(0xFF0D1524), CircleShape)
                .padding(2.dp),
            tint = if (incoming) Color(0xFF82CFFF) else Color(0xFF8899AA),
        )
    }
}

@Composable
private fun NarrativeClickableBlock(
    entry: OverlayReactionLogEntry,
    cluster: OverlayReactionLogCluster,
    scopeLabel: String,
    scopeColor: Color,
    timeLine: String,
    selfUserId: String,
    modifier: Modifier = Modifier,
    alignTimeEnd: Boolean,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.senderUsername.ifBlank {
                    stringResource(R.string.overlay_reaction_sender_unknown)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(6.dp))
            ScopePill(label = scopeLabel, color = scopeColor)
            if (cluster.mergeCount > 1) {
                Spacer(modifier = Modifier.width(4.dp))
                MergeCountPill(count = cluster.mergeCount)
            }
        }
        Text(
            text = overlayReactionLogNarrative(entry, selfUserId, includeSenderName = false),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (timeLine.isNotBlank()) {
            Text(
                text = timeLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = if (alignTimeEnd) TextAlign.End else TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ScopePill(label: String, color: Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun MergeCountPill(count: Int) {
    Text(
        text = stringResource(R.string.overlay_notifications_merge_window, count),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x332A3544))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun OverlayReactionLogAvatarWithPresence(
    userId: String,
    username: String,
    sizeDp: Int = 40,
    isOnline: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        OverlayReactionLogAvatar(
            userId = userId,
            username = username,
            sizeDp = sizeDp,
        )
        OverlayReactionLogPresenceIndicator(
            isOnline = isOnline,
            avatarSizeDp = sizeDp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-2).dp, y = (-2).dp),
        )
    }
}

@Composable
private fun OverlayReactionLogPresenceIndicator(
    isOnline: Boolean,
    avatarSizeDp: Int,
    modifier: Modifier = Modifier,
) {
    val dotOuter = if (avatarSizeDp >= 40) 12.dp else 10.dp
    val dotInner = dotOuter - 3.dp
    val presenceCd = stringResource(
        if (isOnline) {
            R.string.overlay_notifications_presence_online_cd
        } else {
            R.string.overlay_notifications_presence_offline_cd
        },
    )
    Box(
        modifier = modifier.semantics { contentDescription = presenceCd },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(dotOuter)
                .clip(CircleShape)
                .background(ReactionLogCardTokens.presenceRing),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(dotInner)
                    .clip(CircleShape)
                    .background(
                        if (isOnline) {
                            Brush.radialGradient(
                                colors = listOf(
                                    PremiumColors.liveIndicator.copy(alpha = 0.95f),
                                    PremiumColors.liveIndicator,
                                    Color(0xFF16A34A),
                                ),
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(
                                    ReactionLogCardTokens.presenceOffline.copy(alpha = 0.85f),
                                    ReactionLogCardTokens.presenceOffline,
                                ),
                            )
                        },
                    ),
            )
        }
    }
}

@Composable
fun OverlayReactionLogAvatar(
    userId: String,
    username: String,
    sizeDp: Int = 40,
) {
    val context = LocalContext.current
    val telegram = remember(userId) { OverlayTeamContextCache.memberTelegramUsername(userId) }
    val avatarUrl = telegramAvatarUrl(telegram)
    val letter = username.trim().take(1).uppercase().ifBlank { "?" }
    val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt()
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Color(0xFF1E2A3A)),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .size(sizePx, sizePx)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = letter,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEAF0FF),
            )
        }
    }
}

@Composable
fun overlayReactionLogNarrative(
    entry: OverlayReactionLogEntry,
    selfUserId: String,
    includeSenderName: Boolean,
): String {
    val incoming = OverlayReactionLogVisibilityPolicy.isIncoming(entry, selfUserId)
    val targetName = entry.targetUsername?.trim().orEmpty().ifBlank {
        OverlayTeamContextCache.memberUsername(entry.targetUserId.orEmpty()).orEmpty()
    }.ifBlank { stringResource(R.string.overlay_reaction_sender_unknown) }
    return when {
        incoming && entry.visibility == OverlayReactionLogVisibility.Personal ->
            stringResource(R.string.overlay_notifications_narrative_incoming_private)
        incoming && entry.visibility == OverlayReactionLogVisibility.Broadcast ->
            stringResource(R.string.overlay_notifications_narrative_incoming_broadcast)
        !incoming && entry.visibility == OverlayReactionLogVisibility.Personal ->
            stringResource(R.string.overlay_notifications_narrative_outgoing_private, targetName)
        else ->
            stringResource(R.string.overlay_notifications_narrative_outgoing_broadcast)
    }.let { base ->
        if (includeSenderName && incoming) {
            val sender = entry.senderUsername.ifBlank {
                stringResource(R.string.overlay_reaction_sender_unknown)
            }
            "$sender · $base"
        } else {
            base
        }
    }
}
