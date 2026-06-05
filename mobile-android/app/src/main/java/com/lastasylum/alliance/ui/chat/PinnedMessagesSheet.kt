package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(items, key = { it.id }) { preview ->
                    val state = messageStateFor(preview)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = state == PinPreviewDisplayState.Ok) {
                                onJumpTo(preview.id)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = null,
                            tint = if (preview.id == activePinId) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Column(modifier = Modifier.weight(1f)) {
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
                                Text("×", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
