package com.lastasylum.alliance.overlay

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.util.LruCache

/**
 * Reuses paused tile preview hosts for notification list scroll.
 * Key includes [previewHostKey] (log entry id) so multiple visible rows with the same
 * reaction id never share one [FrameLayout] (avoids "child already has a parent" in AndroidView).
 * Animated previews are not cached (always fresh factory).
 */
internal object OverlayReactionTilePreviewPool {
    private const val MAX_ENTRIES = 48

    private val cache = object : LruCache<String, FrameLayout>(MAX_ENTRIES) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: FrameLayout,
            newValue: FrameLayout?,
        ) {
            (oldValue.tag as? ImageView)?.let { stopOverlayReactionTileAnimation(it) }
            detachFromParent(oldValue)
        }
    }

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
    ): FrameLayout {
        if (playAnimatedPreview) {
            return createHost(context, reactionId, playAnimatedPreview = true)
        }
        val key = cacheKey(previewHostKey, reactionId, playAnimatedPreview = false)
        cache.get(key)?.let { cached ->
            detachFromParent(cached)
            if (cached.parent != null) {
                return createHost(context, reactionId, playAnimatedPreview = false)
            }
            (cached.tag as? ImageView)?.let { stopOverlayReactionTileAnimation(it) }
            return cached
        }
        return createHost(context, reactionId, playAnimatedPreview = false)
    }

    fun release(
        previewHostKey: String,
        reactionId: String,
        playAnimatedPreview: Boolean,
        host: FrameLayout,
    ) {
        (host.tag as? ImageView)?.let { stopOverlayReactionTileAnimation(it) }
        detachFromParent(host)
        if (playAnimatedPreview) {
            return
        }
        if (host.parent != null) {
            return
        }
        val key = cacheKey(previewHostKey, reactionId, playAnimatedPreview = false)
        cache.put(key, host)
    }

    fun clear() {
        cache.snapshot().values.forEach { host ->
            (host.tag as? ImageView)?.let { stopOverlayReactionTileAnimation(it) }
            detachFromParent(host)
        }
        cache.evictAll()
    }

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
