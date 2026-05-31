package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionLogEntry
import com.lastasylum.alliance.ui.chat.ChatQuickReactions
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

@Composable
fun OverlayReactionLogReplySheet(
    entry: OverlayReactionLogEntry,
    selfUserId: String,
    onDismiss: () -> Unit,
    onToggleEmoji: (String) -> Unit,
) {
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
                text = stringResource(R.string.chat_sheet_section_reactions),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(ChatQuickReactions.defaults) { emoji ->
                    OutlinedButton(
                        onClick = {
                            onToggleEmoji(emoji)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.widthIn(min = 48.dp),
                    ) {
                        Text(text = emoji, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Text(
                text = overlayReactionLogNarrative(entry, selfUserId, includeSenderName = false),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }
    }
}
