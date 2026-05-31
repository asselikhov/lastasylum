package com.lastasylum.alliance.overlay

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibility

@Composable
fun OverlayReactionLogMiniPreview(
    reactionId: String,
    visibility: OverlayReactionLogVisibility,
    modifier: Modifier = Modifier,
    previewSizeDp: Int = 56,
    showLabel: Boolean = true,
    playAnimatedPreview: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textPayload = remember(reactionId) { decodeTextReactionId(reactionId) }
    val reaction = remember(reactionId) { overlayQuickReactionById(context, reactionId) }
    val label = stringResource(reaction.labelRes)
    val borderColor = when (visibility) {
        OverlayReactionLogVisibility.Personal -> Color(0x995870B8)
        OverlayReactionLogVisibility.Broadcast -> Color(0x9950B860)
    }
    val tileSize = previewSizeDp.dp

    Column(
        modifier = modifier.widthIn(max = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(tileSize)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF141C28))
                .border(1.dp, borderColor, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (textPayload != null) {
                val maxTextWidthPx = remember(density) {
                    (previewSizeDp * density.density).toInt().coerceAtLeast(96)
                }
                AndroidView(
                    modifier = Modifier
                        .widthIn(max = tileSize + 8.dp)
                        .heightIn(min = 40.dp, max = tileSize),
                    factory = { ctx ->
                        OverlayReactionTextBurstUi.createPreviewMessageTextView(
                            ctx,
                            textPayload,
                            maxTextWidthPx,
                        )
                    },
                )
            } else {
                AndroidView(
                    modifier = Modifier.size(tileSize - 8.dp),
                    factory = { ctx ->
                        val icon = createOverlayReactionTileIcon(
                            ctx,
                            reaction,
                            playAnimatedPreview = playAnimatedPreview,
                        )
                        FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            addView(
                                icon,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.Gravity.CENTER,
                                ),
                            )
                            tag = icon
                        }
                    },
                    onRelease = { host ->
                        (host.tag as? ImageView)?.let { stopOverlayReactionTileAnimation(it) }
                    },
                )
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
                modifier = Modifier.widthIn(max = 72.dp),
            )
        }
    }
}
