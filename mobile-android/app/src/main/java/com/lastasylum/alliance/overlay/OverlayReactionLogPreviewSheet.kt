package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionLogCluster
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibilityPolicy
import com.lastasylum.alliance.ui.chat.MessageSheetPreviewSurface
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

@Composable
fun OverlayReactionLogPreviewSheet(
    cluster: OverlayReactionLogCluster,
    selfUserId: String,
    onDismiss: () -> Unit,
) {
    val entry = cluster.representative
    val incoming = OverlayReactionLogVisibilityPolicy.isIncoming(entry, selfUserId)
    val displayName = when {
        incoming -> entry.senderUsername.trim().ifBlank {
            stringResource(R.string.overlay_reaction_sender_unknown)
        }
        else -> stringResource(R.string.overlay_notifications_preview_sender_self)
    }
    val timeLine = formatOverlayReactionLogPreviewTime(entry.createdAt, incoming)

    OverlayAwareBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SquadRelayDimens.contentPaddingHorizontal,
                    vertical = SquadRelayDimens.itemGap,
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MessageSheetPreviewSurface {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (cluster.mergeCount > 1) {
                        OverlayReactionLogStackedPreview(
                            cluster = cluster,
                            previewSizeDp = 72,
                            playAnimatedPreview = true,
                        )
                    } else {
                        OverlayReactionLogMiniPreview(
                            reactionId = entry.reaction,
                            visibility = entry.visibility,
                            previewSizeDp = 72,
                            showLabel = true,
                            playAnimatedPreview = true,
                            compact = false,
                        )
                    }
                }
                if (timeLine.isNotBlank()) {
                    Text(
                        text = timeLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
