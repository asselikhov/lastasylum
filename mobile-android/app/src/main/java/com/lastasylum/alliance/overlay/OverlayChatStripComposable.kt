package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.ui.chat.ChatSenderAvatar
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

@Composable
fun OverlayChatStrip(
    messages: List<ChatMessage>,
    selfUserId: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        messages.forEach { msg ->
            val isMine = !selfUserId.isNullOrBlank() && msg.senderId == selfUserId
            OverlayChatStripMessage(msg = msg, isMine = isMine)
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
    val hasText = msg.text.isNotBlank()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bubbleBg.copy(alpha = 0.92f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChatSenderAvatar(
                telegramUrl = msg.senderTelegramUsername,
                size = 30.dp,
                fallbackName = msg.senderUsername,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = msg.senderUsername.trim().ifBlank { "—" },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = roleAccentColor(msg.senderRole),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (time.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = timeMuted,
                        )
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

