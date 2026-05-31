package com.lastasylum.alliance.overlay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionLogCluster
import com.lastasylum.alliance.data.chat.OverlayReactionLogEntry
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibility
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibilityPolicy
import com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces
import com.lastasylum.alliance.ui.util.telegramAvatarUrl

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverlayReactionLogEntryRow(
    cluster: OverlayReactionLogCluster,
    selfUserId: String,
    unreadHighlight: Boolean,
    onReply: () -> Unit,
    onQuickReply: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = cluster.representative
    val incoming = OverlayReactionLogVisibilityPolicy.isIncoming(entry, selfUserId)
    val outgoingPersonal = !incoming &&
        entry.visibility == OverlayReactionLogVisibility.Personal
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
    val (absolute, relative) = formatOverlayReactionLogTimeLine(entry.createdAt)
    val timeLine = listOf(absolute, relative).filter { it.isNotBlank() }.joinToString(" · ")

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (unreadHighlight) Color(0x1A3D5AFE) else PremiumSurfaces.layer2(),
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(12.dp),
    ) {
        val compact = maxWidth < 340.dp
        if (compact) {
            CompactReactionLogRowContent(
                entry = entry,
                cluster = cluster,
                incoming = incoming,
                outgoingPersonal = outgoingPersonal,
                unreadHighlight = unreadHighlight,
                scopeLabel = scopeLabel,
                scopeColor = scopeColor,
                timeLine = timeLine,
                selfUserId = selfUserId,
                onReply = onReply,
                onQuickReply = onQuickReply,
            )
        } else {
            WideReactionLogRowContent(
                entry = entry,
                cluster = cluster,
                incoming = incoming,
                outgoingPersonal = outgoingPersonal,
                unreadHighlight = unreadHighlight,
                scopeLabel = scopeLabel,
                scopeColor = scopeColor,
                timeLine = timeLine,
                selfUserId = selfUserId,
                onReply = onReply,
                onQuickReply = onQuickReply,
            )
        }
    }
}

@Composable
private fun WideReactionLogRowContent(
    entry: OverlayReactionLogEntry,
    cluster: OverlayReactionLogCluster,
    incoming: Boolean,
    outgoingPersonal: Boolean,
    unreadHighlight: Boolean,
    scopeLabel: String,
    scopeColor: Color,
    timeLine: String,
    selfUserId: String,
    onReply: () -> Unit,
    onQuickReply: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (unreadHighlight) {
            UnreadAccentBar()
        }
        SenderAvatarBlock(entry = entry, incoming = incoming)
        Spacer(modifier = Modifier.width(10.dp))
        NarrativeBlock(
            entry = entry,
            cluster = cluster,
            scopeLabel = scopeLabel,
            scopeColor = scopeColor,
            timeLine = timeLine,
            selfUserId = selfUserId,
            modifier = Modifier.weight(1f),
        )
        if (outgoingPersonal) {
            RecipientMiniAvatar(entry = entry)
            Spacer(modifier = Modifier.width(6.dp))
        }
        OverlayReactionLogMiniPreview(
            reactionId = entry.reaction,
            visibility = entry.visibility,
        )
        if (incoming) {
            IncomingQuickActions(onReply = onReply, onQuickReply = onQuickReply)
        }
    }
}

@Composable
private fun CompactReactionLogRowContent(
    entry: OverlayReactionLogEntry,
    cluster: OverlayReactionLogCluster,
    incoming: Boolean,
    outgoingPersonal: Boolean,
    unreadHighlight: Boolean,
    scopeLabel: String,
    scopeColor: Color,
    timeLine: String,
    selfUserId: String,
    onReply: () -> Unit,
    onQuickReply: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (unreadHighlight) {
                UnreadAccentBar()
            }
            SenderAvatarBlock(entry = entry, incoming = incoming)
            Spacer(modifier = Modifier.width(10.dp))
            NarrativeBlock(
                entry = entry,
                cluster = cluster,
                scopeLabel = scopeLabel,
                scopeColor = scopeColor,
                timeLine = timeLine,
                selfUserId = selfUserId,
                modifier = Modifier.weight(1f),
            )
            if (outgoingPersonal) {
                RecipientMiniAvatar(entry = entry)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = if (unreadHighlight) 12.dp else 0.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlayReactionLogMiniPreview(
                reactionId = entry.reaction,
                visibility = entry.visibility,
            )
            if (incoming) {
                IncomingQuickActions(onReply = onReply, onQuickReply = onQuickReply)
            }
        }
    }
}

@Composable
private fun UnreadAccentBar() {
    Box(
        modifier = Modifier
            .width(4.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF3D5AFE)),
    )
    Spacer(modifier = Modifier.width(8.dp))
}

@Composable
private fun SenderAvatarBlock(
    entry: OverlayReactionLogEntry,
    incoming: Boolean,
) {
    Box(contentAlignment = Alignment.BottomEnd) {
        OverlayReactionLogAvatar(
            userId = entry.senderUserId,
            username = entry.senderUsername,
            sizeDp = 40,
        )
        Icon(
            imageVector = if (incoming) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
            contentDescription = null,
            modifier = Modifier
                .size(14.dp)
                .background(Color(0xFF0D1524), CircleShape)
                .padding(2.dp),
            tint = if (incoming) Color(0xFF82CFFF) else Color(0xFF8899AA),
        )
    }
}

@Composable
private fun RecipientMiniAvatar(entry: OverlayReactionLogEntry) {
    val targetId = entry.targetUserId.orEmpty()
    val targetName = entry.targetUsername.orEmpty().ifBlank {
        OverlayTeamContextCache.memberUsername(targetId).orEmpty()
    }
    if (targetId.isBlank()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "→",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(4.dp))
        OverlayReactionLogAvatar(
            userId = targetId,
            username = targetName,
            sizeDp = 32,
        )
    }
}

@Composable
private fun NarrativeBlock(
    entry: OverlayReactionLogEntry,
    cluster: OverlayReactionLogCluster,
    scopeLabel: String,
    scopeColor: Color,
    timeLine: String,
    selfUserId: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.senderUsername.ifBlank {
                    stringResource(R.string.overlay_reaction_sender_unknown)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (timeLine.isNotBlank()) {
            Text(
                text = timeLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 2.dp),
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
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun MergeCountPill(count: Int) {
    Text(
        text = stringResource(R.string.overlay_reaction_burst_merge_count, count),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x332A3544))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun IncomingQuickActions(
    onReply: () -> Unit,
    onQuickReply: () -> Unit,
) {
    IconButton(onClick = onQuickReply) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = stringResource(R.string.overlay_notifications_quick_reply_cd),
            tint = Color(0xFFE57373),
        )
    }
    IconButton(onClick = onReply) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Reply,
            contentDescription = stringResource(R.string.overlay_notifications_reply_cd),
        )
    }
}

@Composable
fun OverlayReactionLogAvatar(
    userId: String,
    username: String,
    sizeDp: Int = 40,
) {
    val telegram = remember(userId) { OverlayTeamContextCache.memberTelegramUsername(userId) }
    val avatarUrl = telegramAvatarUrl(telegram)
    val letter = username.trim().take(1).uppercase().ifBlank { "?" }
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Color(0xFF1E2A3A)),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
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
