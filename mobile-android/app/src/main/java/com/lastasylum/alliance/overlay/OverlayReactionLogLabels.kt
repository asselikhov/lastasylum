package com.lastasylum.alliance.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionLogEntry
import com.lastasylum.alliance.data.chat.OverlayReactionLogReplyEnricher
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibility

object OverlayReactionLogLabelColors {
    val replyScope = Color(0xFF7EB8FF)
    val personalScope = Color(0xFF9070B8)
    val broadcastScope = Color(0xFF50B860)
}

@Composable
fun overlayReactionLogScopeLabel(entry: OverlayReactionLogEntry): String =
    if (OverlayReactionLogReplyEnricher.isReplyEntry(entry)) {
        stringResource(R.string.overlay_notifications_reply_scope)
    } else {
        when (entry.visibility) {
            OverlayReactionLogVisibility.Personal ->
                stringResource(R.string.overlay_reaction_burst_caption_private)
            OverlayReactionLogVisibility.Broadcast ->
                stringResource(R.string.overlay_reaction_burst_caption_broadcast)
        }
    }

@Composable
fun overlayReactionLogScopeColor(entry: OverlayReactionLogEntry): Color =
    if (OverlayReactionLogReplyEnricher.isReplyEntry(entry)) {
        OverlayReactionLogLabelColors.replyScope
    } else {
        when (entry.visibility) {
            OverlayReactionLogVisibility.Personal -> OverlayReactionLogLabelColors.personalScope
            OverlayReactionLogVisibility.Broadcast -> OverlayReactionLogLabelColors.broadcastScope
        }
    }
