package com.lastasylum.alliance.overlay

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun OverlayReactionTilePreviewHost(
    previewHostKey: String,
    reactionId: String,
    playAnimatedPreview: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    key(previewHostKey, reactionId, playAnimatedPreview) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                OverlayReactionTilePreviewPool.obtain(
                    ctx,
                    previewHostKey,
                    reactionId,
                    playAnimatedPreview,
                )
            },
            update = { host ->
                val icon = host.tag as? ImageView ?: return@AndroidView
                if (playAnimatedPreview && overlayReactionSupportsAnimatedPreview(context, reactionId)) {
                    resumeOverlayReactionTilePreview(icon)
                } else {
                    stopOverlayReactionTileAnimation(icon)
                }
            },
            onReset = { host ->
                (host.parent as? ViewGroup)?.removeView(host)
            },
            onRelease = { host ->
                OverlayReactionTilePreviewPool.release(
                    previewHostKey,
                    reactionId,
                    playAnimatedPreview,
                    host,
                )
            },
        )
    }
}
