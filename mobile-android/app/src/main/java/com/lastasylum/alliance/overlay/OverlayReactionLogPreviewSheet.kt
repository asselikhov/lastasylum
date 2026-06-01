package com.lastasylum.alliance.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionLogCluster
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibility
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
    val context = LocalContext.current
    val textPayload = remember(entry.reaction) { decodeTextReactionId(entry.reaction) }
    val reaction = remember(entry.reaction) { overlayQuickReactionById(context, entry.reaction) }
    val reactionLabel = stringResource(reaction.labelRes)
    val borderColor = when (entry.visibility) {
        OverlayReactionLogVisibility.Personal -> Color(0x995870B8)
        OverlayReactionLogVisibility.Broadcast -> Color(0x9950B860)
    }

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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (cluster.mergeCount > 1) {
                        OverlayReactionLogStackedPreview(
                            cluster = cluster,
                            previewSizeDp = 72,
                            playAnimatedPreview = true,
                        )
                    } else if (textPayload != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF141C28),
                            shadowElevation = 2.dp,
                            border = BorderStroke(1.dp, borderColor),
                            modifier = Modifier.widthIn(max = 280.dp),
                        ) {
                            Text(
                                text = textPayload,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    } else {
                        OverlayReactionLogMiniPreview(
                            reactionId = entry.reaction,
                            visibility = entry.visibility,
                            previewSizeDp = 72,
                            showLabel = false,
                            playAnimatedPreview = true,
                            compact = false,
                        )
                    }
                    Text(
                        text = reactionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (timeLine.isNotBlank()) {
                        Text(
                            text = timeLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
