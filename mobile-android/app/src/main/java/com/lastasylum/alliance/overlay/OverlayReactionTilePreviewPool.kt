package com.lastasylum.alliance.overlay

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.util.LruCache

/**
 * Reuses paused tile preview hosts for notification list scroll.
 * Animated previews are not cached (always fresh factory).
 */
internal object OverlayReactionTilePreviewPool {
    private const val MAX_ENTRIES = 32
    private val cache = LruCache<String, FrameLayout>(MAX_ENTRIES)

    fun cacheKey(reactionId: String, playAnimatedPreview: Boolean): String =
        "$reactionId|animated=$playAnimatedPreview"

    fun obtain(
        context: Context,
        reactionId: String,
        playAnimatedPreview: Boolean,
    ): FrameLayout {
        if (playAnimatedPreview) {
            return createHost(context, reactionId, playAnimatedPreview = true)
        }
        val key = cacheKey(reactionId, playAnimatedPreview = false)
        cache.get(key)?.let { cached ->
            (cached.tag as? ImageView)?.let { stopOverlayReactionTileAnimation(it) }
            return cached
        }
        return createHost(context, reactionId, playAnimatedPreview = false).also {
            cache.put(key, it)
        }
    }

    fun release(reactionId: String, playAnimatedPreview: Boolean, host: FrameLayout) {
        if (playAnimatedPreview) {
            (host.tag as? ImageView)?.let { stopOverlayReactionTileAnimation(it) }
            return
        }
        val key = cacheKey(reactionId, playAnimatedPreview = false)
        (host.tag as? ImageView)?.let { stopOverlayReactionTileAnimation(it) }
        cache.put(key, host)
    }

    fun clear() {
        cache.snapshot().values.forEach { host ->
            (host.tag as? ImageView)?.let { stopOverlayReactionTileAnimation(it) }
        }
        cache.evictAll()
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
