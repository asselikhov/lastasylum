package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.data.isObjectIdNewer

fun isChatMessageReadByPeer(messageId: String?, otherReadUptoMessageId: String?): Boolean {
    val mid = messageId?.trim().orEmpty()
    val cursor = otherReadUptoMessageId?.trim().orEmpty()
    if (mid.isEmpty() || cursor.isEmpty()) return false
    return !isObjectIdNewer(mid, cursor)
}

@Composable
fun ChatMessageReadStatus(
    messageId: String?,
    otherReadUptoMessageId: String?,
    modifier: Modifier = Modifier,
    mutedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
    readColor: Color = Color(0xFF5EB3F6),
) {
    val mid = messageId?.trim().orEmpty()
    if (mid.isEmpty() || mid.startsWith("pending-")) return
    val read = isChatMessageReadByPeer(mid, otherReadUptoMessageId)
    Text(
        text = if (read) "✓✓" else "✓",
        style = MaterialTheme.typography.labelSmall,
        color = if (read) readColor else mutedColor,
        modifier = modifier,
    )
}

@Composable
fun ChatMessageTimeWithReadStatus(
    time: String,
    isMine: Boolean,
    isChainBottom: Boolean,
    messageId: String?,
    otherReadUptoMessageId: String?,
    timeColor: Color,
    modifier: Modifier = Modifier,
    readMutedColor: Color = timeColor.copy(alpha = 0.72f),
    readColor: Color = Color(0xFF5EB3F6),
) {
    if (time.isBlank() && (!isMine || !isChainBottom)) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (time.isNotBlank()) {
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = timeColor,
            )
        }
        if (isMine && isChainBottom && !messageId.isNullOrBlank()) {
            if (time.isNotBlank()) Spacer(Modifier.width(4.dp))
            ChatMessageReadStatus(
                messageId = messageId,
                otherReadUptoMessageId = otherReadUptoMessageId,
                mutedColor = readMutedColor,
                readColor = readColor,
            )
        }
    }
}

@Composable
fun ChatMessageTimeOverlayChip(
    time: String,
    isMine: Boolean,
    isChainBottom: Boolean,
    messageId: String?,
    otherReadUptoMessageId: String?,
    modifier: Modifier = Modifier,
) {
    if (time.isBlank() && (!isMine || !isChainBottom || messageId.isNullOrBlank())) return
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier,
    ) {
        ChatMessageTimeWithReadStatus(
            time = time,
            isMine = isMine,
            isChainBottom = isChainBottom,
            messageId = messageId,
            otherReadUptoMessageId = otherReadUptoMessageId,
            timeColor = Color.White,
            readMutedColor = Color.White.copy(alpha = 0.72f),
            readColor = Color(0xFF8FD4FF),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}
