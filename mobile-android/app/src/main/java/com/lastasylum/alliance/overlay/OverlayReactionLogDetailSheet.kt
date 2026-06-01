package com.lastasylum.alliance.overlay

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionLogCluster
import com.lastasylum.alliance.data.chat.OverlayReactionLogEntry
import com.lastasylum.alliance.data.chat.OverlayReactionLogReplyEnricher
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibilityPolicy
import com.lastasylum.alliance.ui.chat.ChatMessageReactionsRow
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

@Composable
fun OverlayReactionLogDetailSheet(
    cluster: OverlayReactionLogCluster,
    selfUserId: String,
    onDismiss: () -> Unit,
    onReplyToReactionLog: ((OverlayReactionLogEntry) -> Unit)? = null,
    onToggleEmojiReaction: ((String) -> Unit)? = null,
) {
    val entry = cluster.representative
    val incoming = OverlayReactionLogVisibilityPolicy.isIncoming(entry, selfUserId)
    OverlayAwareBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SquadRelayDimens.contentPaddingHorizontal,
                    vertical = SquadRelayDimens.itemGap,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OverlayReactionLogCard(
                incoming = incoming,
                unreadHighlight = false,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (cluster.mergeCount > 1) {
                        OverlayReactionLogStackedPreview(
                            cluster = cluster,
                            previewSizeDp = 72,
                        )
                    } else {
                        OverlayReactionLogMiniPreview(
                            reactionId = entry.reaction,
                            visibility = entry.visibility,
                            previewSizeDp = 140,
                            showLabel = false,
                            playAnimatedPreview = true,
                            compact = false,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = overlayReactionLogNarrative(entry, selfUserId, includeSenderName = true),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            val timeLine = formatOverlayReactionLogTimeLabel(entry.createdAt)
            if (timeLine.isNotBlank()) {
                Text(
                    text = timeLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (entry.reactions.isNotEmpty()) {
                ChatMessageReactionsRow(
                    reactions = entry.reactions,
                    onReactionToggle = onToggleEmojiReaction,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (cluster.mergeCount > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.overlay_notifications_detail_merge_timeline),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                ) {
                    cluster.entries.forEach { item ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OverlayReactionLogMiniPreview(
                                reactionId = item.reaction,
                                visibility = item.visibility,
                                previewSizeDp = 56,
                                showLabel = false,
                                playAnimatedPreview = false,
                                compact = false,
                            )
                            val itemTime = formatOverlayReactionLogTimeLabel(item.createdAt)
                            if (itemTime.isNotBlank()) {
                                Text(
                                    text = itemTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (incoming && onReplyToReactionLog != null && !OverlayReactionLogReplyEnricher.isReplyEntry(entry)) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onDismiss()
                        onReplyToReactionLog(entry)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.overlay_notifications_reply))
                }
            }
        }
    }
}
