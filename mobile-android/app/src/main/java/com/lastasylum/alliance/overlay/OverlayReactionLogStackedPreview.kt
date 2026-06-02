package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.data.chat.OverlayReactionLogCluster
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibility

@Composable
fun OverlayReactionLogStackedPreview(
    cluster: OverlayReactionLogCluster,
    previewSizeDp: Int = 56,
    playAnimatedPreview: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val entries = cluster.entries.take(3)
    val boxSize = (previewSizeDp + (entries.size - 1) * 10).dp
    Box(modifier = modifier.size(boxSize)) {
        entries.forEachIndexed { index, entry ->
            OverlayReactionLogMiniPreview(
                reactionId = entry.reaction,
                visibility = entry.visibility,
                previewSizeDp = previewSizeDp - index * 4,
                showLabel = false,
                playAnimatedPreview = playAnimatedPreview && index == 0,
                compact = true,
                modifier = Modifier.offset(x = (index * 10).dp, y = (index * 2).dp),
            )
        }
    }
}
