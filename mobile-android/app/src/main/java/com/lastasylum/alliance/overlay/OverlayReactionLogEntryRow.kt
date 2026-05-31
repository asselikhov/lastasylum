package com.lastasylum.alliance.overlay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionLogCluster
import com.lastasylum.alliance.data.chat.OverlayReactionLogEntry
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibility
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibilityPolicy
import com.lastasylum.alliance.ui.theme.premium.PremiumColors
import com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import kotlinx.coroutines.delay

private object ReactionLogCardTokens {
    val corner = 14.dp
    val borderDefault = Color(0x403D4A62)
    val borderUnread = Color(0x5538BDF8)
    val incomingGradientTop = Color(0x2418A4A0)
    val incomingGradientBottom = Color(0x080E1624)
    val outgoingGradientTop = Color(0x14FFFFFF)
    val outgoingGradientBottom = Color(0x060E1624)
    val unreadAccentStart = Color(0xFF38BDF8)
    val unreadAccentEnd = Color(0xFF9070B8)
    val presenceOffline = Color(0xFF667788)
    val presenceRing = Color(0xFF0E1624)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverlayReactionLogEntryRow(
    cluster: OverlayReactionLogCluster,
    selfUserId: String,
    unreadHighlight: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
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

    val cardShape = RoundedCornerShape(ReactionLogCardTokens.corner)
    val cardGradient = if (incoming) {
        Brush.verticalGradient(
            listOf(ReactionLogCardTokens.incomingGradientTop, ReactionLogCardTokens.incomingGradientBottom),
        )
    } else {
        Brush.verticalGradient(
            listOf(ReactionLogCardTokens.outgoingGradientTop, ReactionLogCardTokens.outgoingGradientBottom),
        )
    }
    val borderColor = when {
        unreadHighlight && incoming -> ReactionLogCardTokens.borderUnread
        else -> ReactionLogCardTokens.borderDefault
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(PremiumSurfaces.layer2())
            .background(cardGradient)
            .border(1.dp, borderColor, cardShape)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.combinedClickable(onClick = onClick)
                },
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
                unreadHighlight = unreadHighlight && incoming,
                scopeLabel = scopeLabel,
                scopeColor = scopeColor,
                timeLine = timeLine,
                selfUserId = selfUserId,
            )
        } else {
            WideReactionLogRowContent(
                entry = entry,
                cluster = cluster,
                incoming = incoming,
                outgoingPersonal = outgoingPersonal,
                unreadHighlight = unreadHighlight && incoming,
                scopeLabel = scopeLabel,
                scopeColor = scopeColor,
                timeLine = timeLine,
                selfUserId = selfUserId,
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
            showLabel = false,
        )
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
                showLabel = false,
            )
        }
    }
}

@Composable
private fun UnreadAccentBar() {
    Box(
        modifier = Modifier
            .width(3.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        ReactionLogCardTokens.unreadAccentStart,
                        ReactionLogCardTokens.unreadAccentEnd,
                    ),
                ),
            ),
    )
    Spacer(modifier = Modifier.width(8.dp))
}

@Composable
private fun SenderAvatarBlock(
    entry: OverlayReactionLogEntry,
    incoming: Boolean,
) {
    Box(contentAlignment = Alignment.BottomEnd) {
        OverlayReactionLogAvatarWithPresence(
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
        OverlayReactionLogAvatarWithPresence(
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
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
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
            .background(color.copy(alpha = 0.16f))
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
fun OverlayReactionLogAvatarWithPresence(
    userId: String,
    username: String,
    sizeDp: Int = 40,
    modifier: Modifier = Modifier,
) {
    val presenceRevision by OverlayTeamPresenceCache.revision.collectAsState()
    val teamRevision by OverlayTeamContextCache.revision.collectAsState()
    var freshnessTick by remember(userId) { mutableIntStateOf(0) }
    LaunchedEffect(userId) {
        while (true) {
            delay(30_000L)
            freshnessTick++
        }
    }
    val isOnline = remember(userId, presenceRevision, teamRevision, freshnessTick) {
        OverlayMemberPresenceLookup.isInGameNow(userId)
    }
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
