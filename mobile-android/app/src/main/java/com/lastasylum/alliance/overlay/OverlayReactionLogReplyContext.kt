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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionLogReplyTo

enum class OverlayReactionLogReplyContextLayout {
    /** Detail / preview sheet — row under main content. */
    Compact,
    /** Under the reaction preview on notification cards. */
    UnderPreview,
}

@Composable
fun OverlayReactionLogReplyContext(
    replyTo: OverlayReactionLogReplyTo,
    modifier: Modifier = Modifier,
    layout: OverlayReactionLogReplyContextLayout = OverlayReactionLogReplyContextLayout.Compact,
    senderUsername: String? = null,
    previewSizeDp: Int = 32,
) {
    when (layout) {
        OverlayReactionLogReplyContextLayout.Compact ->
            ReplyContextRow(
                replyTo = replyTo,
                modifier = modifier.padding(top = 4.dp),
                previewSizeDp = previewSizeDp,
                horizontalAlignment = Alignment.CenterHorizontally,
            )
        OverlayReactionLogReplyContextLayout.UnderPreview ->
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalAlignment = Alignment.End,
            ) {
                val nick = senderUsername?.trim().orEmpty()
                if (nick.isNotEmpty()) {
                    Text(
                        text = nick,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ReplyContextRow(
                    replyTo = replyTo,
                    modifier = Modifier.padding(top = if (nick.isNotEmpty()) 2.dp else 0.dp),
                    previewSizeDp = 28,
                    horizontalAlignment = Alignment.End,
                )
            }
    }
}

@Composable
private fun ReplyContextRow(
    replyTo: OverlayReactionLogReplyTo,
    modifier: Modifier = Modifier,
    previewSizeDp: Int,
    horizontalAlignment: Alignment.Horizontal,
) {
    Row(
        modifier = modifier
            .then(
                if (horizontalAlignment == Alignment.End) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, horizontalAlignment),
    ) {
        OverlayReactionLogMiniPreview(
            reactionId = replyTo.reaction,
            visibility = replyTo.visibility,
            showLabel = false,
            playAnimatedPreview = false,
            previewSizeDp = previewSizeDp,
        )
    }
}
