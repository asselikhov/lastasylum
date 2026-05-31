package com.lastasylum.alliance.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionLogEntry
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

@Composable
fun OverlayReactionLogReplySheet(
    entry: OverlayReactionLogEntry,
    onDismiss: () -> Unit,
    onSendReaction: (reactionId: String) -> Unit,
    onMoreReactions: () -> Unit,
) {
    val context = LocalContext.current
    val quickReactions = remember(context) {
        overlayAnimationReactions().take(10)
    }
    OverlayAwareBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SquadRelayDimens.contentPaddingHorizontal,
                    vertical = SquadRelayDimens.itemGap,
                ),
        ) {
            Text(
                text = entry.senderUsername.ifBlank {
                    stringResource(R.string.overlay_reaction_sender_unknown)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.overlay_notifications_pick_reaction),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(quickReactions, key = { it.id }) { reaction ->
                    OverlayReactionLogMiniPreview(
                        reactionId = reaction.id,
                        visibility = entry.visibility,
                        previewSizeDp = 52,
                        showLabel = false,
                        modifier = Modifier.clickable {
                            onSendReaction(reaction.id)
                            onDismiss()
                        },
                    )
                }
            }
            TextButton(
                onClick = {
                    onDismiss()
                    onMoreReactions()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.overlay_notifications_more_reactions),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
