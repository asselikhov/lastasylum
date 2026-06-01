package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

@Composable
fun OverlayReactionLogPreviewSheet(
    cluster: OverlayReactionLogCluster,
    selfUserId: String,
    onDismiss: () -> Unit,
) {
    val entry = cluster.representative
    val incoming = com.lastasylum.alliance.data.chat.OverlayReactionLogVisibilityPolicy
        .isIncoming(entry, selfUserId)
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
                            playAnimatedPreview = true,
                        )
                    } else {
                        OverlayReactionLogMiniPreview(
                            reactionId = entry.reaction,
                            visibility = entry.visibility,
                            previewSizeDp = 140,
                            showLabel = true,
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
            val timeLine = formatOverlayReactionLogTimeLabelCompact(entry.createdAt)
            if (timeLine.isNotBlank()) {
                Text(
                    text = timeLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
