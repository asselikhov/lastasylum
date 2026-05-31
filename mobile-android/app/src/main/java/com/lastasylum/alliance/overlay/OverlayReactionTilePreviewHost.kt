package com.lastasylum.alliance.overlay

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun OverlayReactionTilePreviewHost(
    reactionId: String,
    playAnimatedPreview: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reaction = remember(reactionId) { overlayQuickReactionById(context, reactionId) }
    AndroidView(
        modifier = modifier,
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
        update = { host ->
            val icon = host.tag as? ImageView ?: return@AndroidView
            if (playAnimatedPreview) {
                resumeOverlayReactionTilePreview(icon)
            } else {
                stopOverlayReactionTileAnimation(icon)
            }
        },
        onRelease = { host ->
            (host.tag as? ImageView)?.let { stopOverlayReactionTileAnimation(it) }
        },
    )
}
