package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto
import com.lastasylum.alliance.data.chat.chatSenderDisplayWithTag
import com.lastasylum.alliance.overlay.OverlayAwareBottomSheet

@Composable
fun PinnedMessagesSheet(
    visible: Boolean,
    items: List<PinnedMessagePreviewDto>,
    canModerate: Boolean,
    activePinId: String?,
    onDismiss: () -> Unit,
    onJumpTo: (String) -> Unit,
    onUnpinOne: (String) -> Unit,
    onUnpinAll: () -> Unit,
    onHideBar: () -> Unit,
    messageStateFor: (PinnedMessagePreviewDto) -> PinPreviewDisplayState,
) {
    if (!visible) return
    OverlayAwareBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_pinned_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            if (canModerate && items.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onHideBar) {
                        Text(stringResource(R.string.chat_pinned_hide_bar))
                    }
                    TextButton(onClick = onUnpinAll) {
                        Text(stringResource(R.string.chat_pinned_unpin_all))
                    }
                }
            }
            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(items.size) { index ->
                    val preview = items[index]
                    val state = messageStateFor(preview)
                    val isActive = preview.id == activePinId
                    val sender = chatSenderDisplayWithTag(
                        preview.senderTeamTag,
                        preview.senderUsername,
                        preview.senderServerNumber,
                    )
                    val body = when (state) {
                        PinPreviewDisplayState.Deleted ->
                            stringResource(R.string.chat_pinned_preview_deleted)
                        PinPreviewDisplayState.Unavailable ->
                            stringResource(R.string.chat_pinned_preview_unavailable)
                        PinPreviewDisplayState.Ok -> pinnedPreviewLabel(
                            preview = preview,
                            messageDeleted = false,
                            messageUnavailable = false,
                        )
                    }
                    val thumbUrl = preview.resolvedThumbnailUrl()
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        onClick = {
                            if (state == PinPreviewDisplayState.Ok) {
                                onJumpTo(preview.id)
                                onDismiss()
                            }
                        },
                        enabled = state == PinPreviewDisplayState.Ok,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (!thumbUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = thumbUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.PushPin,
                                    contentDescription = null,
                                    tint = if (isActive) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                if (isActive) {
                                    Text(
                                        text = stringResource(R.string.chat_pinned_active_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                if (sender.isNotBlank()) {
                                    Text(
                                        text = sender,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (canModerate) {
                                IconButton(onClick = { onUnpinOne(preview.id) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.chat_action_unpin),
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
