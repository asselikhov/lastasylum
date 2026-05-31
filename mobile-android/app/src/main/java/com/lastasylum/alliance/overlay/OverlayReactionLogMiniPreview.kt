package com.lastasylum.alliance.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibility

@Composable
fun OverlayReactionLogMiniPreview(
    reactionId: String,
    visibility: OverlayReactionLogVisibility,
    modifier: Modifier = Modifier,
    previewSizeDp: Int = 56,
    showLabel: Boolean = true,
    playAnimatedPreview: Boolean = true,
    compact: Boolean = true,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textPayload = remember(reactionId) { decodeTextReactionId(reactionId) }
    val reaction = remember(reactionId) { overlayQuickReactionById(context, reactionId) }
    val label = stringResource(reaction.labelRes)
    val previewCd = stringResource(R.string.overlay_reaction_preview_cd, label)
    val borderColor = when (visibility) {
        OverlayReactionLogVisibility.Personal -> Color(0x995870B8)
        OverlayReactionLogVisibility.Broadcast -> Color(0x9950B860)
    }
    val tileSize = previewSizeDp.dp
    val maxColumnWidth = if (compact) 72.dp else 220.dp
    val previewBackground = if (textPayload != null) {
        Color(0xFF141C28)
    } else {
        Color.Transparent
    }

    Column(
        modifier = modifier.widthIn(max = maxColumnWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = previewBackground,
            shadowElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
            modifier = Modifier.semantics { contentDescription = previewCd },
        ) {
            Box(
                modifier = Modifier
                    .size(tileSize)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (textPayload != null) {
                    if (playAnimatedPreview) {
                        val maxTextWidthPx = remember(density, previewSizeDp) {
                            (previewSizeDp * density.density).toInt().coerceAtLeast(96)
                        }
                        AndroidView(
                            modifier = Modifier.size(tileSize),
                            factory = { ctx ->
                                OverlayReactionTextBurstUi.createPreviewMessageTextView(
                                    ctx,
                                    textPayload,
                                    maxTextWidthPx,
                                )
                            },
                        )
                    } else {
                        Text(
                            text = textPayload,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .widthIn(max = tileSize)
                                .padding(horizontal = 4.dp),
                        )
                    }
                } else {
                    OverlayReactionTilePreviewHost(
                        reactionId = reactionId,
                        playAnimatedPreview = playAnimatedPreview,
                        modifier = Modifier.size(tileSize),
                    )
                }
            }
        }
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = maxColumnWidth),
            )
        }
    }
}
