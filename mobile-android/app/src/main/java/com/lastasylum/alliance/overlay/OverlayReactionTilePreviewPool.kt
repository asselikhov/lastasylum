package com.lastasylum.alliance.overlay

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Creates tile preview hosts for notification list rows.
 * Hosts are not cached: reusing [FrameLayout] across [AndroidView] attach cycles (expand/collapse,
 * LazyColumn scroll) caused "child already has a parent" crashes.
 */
internal object OverlayReactionTilePreviewPool {
    fun cacheKey(
        previewHostKey: String,
        reactionId: String,
        playAnimatedPreview: Boolean,
    ): String = "$previewHostKey|$reactionId|animated=$playAnimatedPreview"

    fun obtain(
        context: Context,
        previewHostKey: String,
        reactionId: String,
        playAnimatedPreview: Boolean,
    ): FrameLayout = createHost(context, reactionId, playAnimatedPreview)

    fun release(
        previewHostKey: String,
        reactionId: String,
        playAnimatedPreview: Boolean,
        host: FrameLayout,
    ) {
        (host.tag as? ImageView)?.let { stopOverlayReactionTileAnimation(it) }
        detachFromParent(host)
    }

    fun clear() = Unit

    private fun detachFromParent(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private fun createHost(
        context: Context,
        reactionId: String,
        playAnimatedPreview: Boolean,
    ): FrameLayout {
        val reaction = overlayQuickReactionById(context, reactionId)
        val icon = createOverlayReactionTileIcon(
            context,
            reaction,
            playAnimatedPreview = playAnimatedPreview,
        )
        return FrameLayout(context).apply {
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
    }
}
