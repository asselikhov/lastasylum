package com.lastasylum.alliance.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.chatSenderDisplayWithTag
import com.lastasylum.alliance.data.chat.stickers.ZlobyakaStickerPack
import com.lastasylum.alliance.ui.chat.ChatSenderAvatar
import com.lastasylum.alliance.ui.chat.RoleBadge
import com.lastasylum.alliance.ui.chat.TelegramLikeAttachmentsGrid
import com.lastasylum.alliance.ui.chat.resolvedChatAttachmentImageUrl
import com.lastasylum.alliance.ui.chat.formatChatTime
import com.lastasylum.alliance.ui.theme.ChatTelegramIncomingBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramIncomingOnBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramOutgoingBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramOutgoingOnBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramTimeMuted
import com.lastasylum.alliance.ui.theme.ChatTelegramTimeMutedIncoming
import com.lastasylum.alliance.ui.theme.roleAccentColor
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import kotlinx.coroutines.delay

@Composable
fun OverlayChatStrip(
    messages: List<ChatMessage>,
    selfUserId: String?,
    modifier: Modifier = Modifier,
) {
    val keep = remember { mutableStateListOf<ChatMessage>() }
    val leaving = remember { mutableStateMapOf<String, Boolean>() }
    val latestMessages by rememberUpdatedState(messages)
    val latestSelfId by rememberUpdatedState(selfUserId)

    fun keyOf(msg: ChatMessage): String =
        msg._id?.takeIf { it.isNotBlank() } ?: msg.stableKey()

    LaunchedEffect(latestMessages) {
        // Upsert new / updated
        latestMessages.forEach { m ->
            val key = keyOf(m)
            leaving.remove(key)
            val i = keep.indexOfFirst { keyOf(it) == key }
            if (i >= 0) keep[i] = m else keep.add(m)
        }
        // Mark removed for exit animation
        val currentKeys = latestMessages.map { keyOf(it) }.toSet()
        val removed = keep.filter { keyOf(it) !in currentKeys }
        removed.forEach { m -> leaving[keyOf(m)] = true }
        if (removed.isNotEmpty()) {
            delay(220)
            keep.removeAll { keyOf(it) !in currentKeys }
            removed.forEach { m -> leaving.remove(keyOf(m)) }
        }
        // Keep order consistent with input list
        val order = latestMessages.map { keyOf(it) }
        keep.sortBy { order.indexOf(keyOf(it)).let { idx -> if (idx < 0) Int.MAX_VALUE else idx } }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        keep.forEach { msg ->
            val key = keyOf(msg)
            val isMine = !latestSelfId.isNullOrBlank() && msg.senderId == latestSelfId
            val isLeaving = leaving[key] == true
            AnimatedVisibility(
                visible = !isLeaving,
                enter = fadeIn(animationSpec = tween(140)) +
                    slideInVertically(animationSpec = tween(160)) { it / 2 } +
                    scaleIn(initialScale = 0.96f, animationSpec = tween(160)),
                exit = fadeOut(animationSpec = tween(170)) +
                    slideOutVertically(animationSpec = tween(190)) { it / 3 } +
                    scaleOut(targetScale = 0.98f, animationSpec = tween(180)),
            ) {
                OverlayChatStripMessage(msg = msg, isMine = isMine)
            }
        }
    }
}

@Composable
private fun OverlayChatStripMessage(
    msg: ChatMessage,
    isMine: Boolean,
) {
    val bubbleBg = if (isMine) ChatTelegramOutgoingBubble else ChatTelegramIncomingBubble
    val onBubble = if (isMine) ChatTelegramOutgoingOnBubble else ChatTelegramIncomingOnBubble
    val timeMuted = if (isMine) ChatTelegramTimeMuted else ChatTelegramTimeMutedIncoming
    val time = remember(msg.createdAt) { formatChatTime(msg.createdAt) }
    val images = remember(msg.attachments) {
        msg.attachments.filter { it.kind == "image" && it.url.isNotBlank() }
    }
    val imageUrls = remember(images) { images.map { resolvedChatAttachmentImageUrl(it.url) } }
    val stickerStem = remember(msg.text) { ZlobyakaStickerPack.parseStem(msg.text) }
    val hasSticker = stickerStem != null
    val hasText = !hasSticker && msg.text.isNotBlank()
    val displayName = remember(msg.senderTeamTag, msg.senderUsername) {
        chatSenderDisplayWithTag(msg.senderTeamTag, msg.senderUsername).trim().ifBlank { "—" }
    }
    val avatarUrl = remember(msg.senderTelegramUsername) {
        telegramAvatarUrl(msg.senderTelegramUsername)
    }
    val role = remember(msg.senderRole) { msg.senderRole.trim() }
    val border = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bubbleBg, // non-transparent per requirement
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ChatSenderAvatar(
                    telegramUrl = avatarUrl,
                    size = 30.dp,
                    fallbackName = msg.senderUsername,
                )
                if (time.isNotBlank()) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = timeMuted,
                        maxLines = 1,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = roleAccentColor(role),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (role.isNotBlank()) {
                        // Rank in top-right corner
                        RoleBadge(role = role)
                    }
                }

                if (hasSticker && stickerStem != null) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = lerp(bubbleBg, Color.Black, if (isMine) 0.08f else 0.12f),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 96.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth(),
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(ctx)
                                    .data(ZlobyakaStickerPack.assetUriForStem(stickerStem))
                                    .crossfade(true)
                                    .size(256)
                                    .build(),
                                contentDescription = "sticker",
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
                if (imageUrls.isNotEmpty()) {
                    TelegramLikeAttachmentsGrid(
                        urls = imageUrls,
                        contentDescription = "chat attachment",
                        onOpen = {},
                        roundTileCorners = true,
                        bottomRound = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (hasText) {
                    Text(
                        text = msg.text.trimEnd(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onBubble,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

