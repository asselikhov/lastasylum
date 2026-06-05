package com.lastasylum.alliance.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto
import com.lastasylum.alliance.data.chat.chatSenderDisplayWithTag

@Composable
fun pinnedPreviewLabel(
    preview: PinnedMessagePreviewDto,
    messageDeleted: Boolean = false,
): String {
    if (messageDeleted) return stringResource(R.string.chat_pinned_preview_deleted)
    return when {
        preview.isSticker -> stringResource(R.string.chat_pinned_preview_sticker)
        preview.hasImage && preview.text.isBlank() ->
            stringResource(R.string.chat_pinned_preview_photo)
        preview.text.isNotBlank() -> preview.text
        preview.hasImage -> stringResource(R.string.chat_pinned_preview_photo)
        else -> stringResource(R.string.chat_sheet_preview_empty)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinnedMessageBar(
    preview: PinnedMessagePreviewDto,
    canUnpin: Boolean,
    onTap: () -> Unit,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier,
    historyCount: Int = 0,
    messageDeleted: Boolean = false,
    thumbnailUrl: String? = null,
    pinnedMetaLine: String? = null,
) {
    AnimatedContent(
        targetState = preview.id,
        modifier = modifier,
        label = "pinnedBarPreview",
    ) { _ ->
        PinnedMessageBarContent(
            preview = preview,
            canUnpin = canUnpin,
            onTap = onTap,
            onUnpin = onUnpin,
            historyCount = historyCount,
            messageDeleted = messageDeleted,
            thumbnailUrl = thumbnailUrl,
            pinnedMetaLine = pinnedMetaLine,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedMessageBarContent(
    preview: PinnedMessagePreviewDto,
    canUnpin: Boolean,
    onTap: () -> Unit,
    onUnpin: () -> Unit,
    historyCount: Int,
    messageDeleted: Boolean,
    thumbnailUrl: String?,
    pinnedMetaLine: String?,
) {
    val senderLine = chatSenderDisplayWithTag(
        preview.senderTeamTag,
        preview.senderUsername,
        preview.senderServerNumber,
    )
    val bodyLine = pinnedPreviewLabel(preview, messageDeleted)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                onClick = onTap,
                onLongClick = if (canUnpin) onUnpin else null,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = Icons.Outlined.PushPin,
                contentDescription = stringResource(R.string.chat_pinned_bar_cd),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            if (historyCount > 1) {
                Text(
                    text = historyCount.coerceAtMost(99).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            if (senderLine.isNotBlank()) {
                Text(
                    text = senderLine,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = bodyLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!pinnedMetaLine.isNullOrBlank()) {
                Text(
                    text = pinnedMetaLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (canUnpin) {
            IconButton(
                onClick = onUnpin,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.chat_action_unpin),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
