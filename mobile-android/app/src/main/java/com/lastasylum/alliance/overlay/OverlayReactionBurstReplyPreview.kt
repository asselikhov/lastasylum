package com.lastasylum.alliance.overlay

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionBurstReplyTo

internal object OverlayReactionBurstReplyPreview {
    /** Parent reaction thumbnail to the right of scope + from lines; height matches both lines. */
    fun attachBesideCaptionLines(
        caption: OverlayReactionHeroCaptionBlock,
        context: Context,
        replyTo: OverlayReactionBurstReplyTo,
        visualFactory: OverlayReactionVisualFactory,
        dp: (Int) -> Int,
        hero: Boolean,
    ) {
        val root = caption.root
        if (root.orientation == LinearLayout.HORIZONTAL) return

        root.removeView(caption.textColumn)
        root.orientation = LinearLayout.HORIZONTAL
        root.gravity = Gravity.CENTER_VERTICAL

        val previewHost = SquareHeightFrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            contentDescription = context.getString(
                R.string.overlay_reaction_burst_reply_parent_preview,
                replyTo.reaction,
            )
        }
        val reaction = overlayQuickReactionById(context, replyTo.reaction)
        val fallbackPx = dp(if (hero) 32 else 24)
        val animView = visualFactory.createAnimView(reaction, fallbackPx, playLottie = false)
        previewHost.addView(
            animView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ),
        )

        root.addView(
            caption.textColumn,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )
        root.addView(
            previewHost,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            ).apply {
                marginStart = dp(6)
            },
        )
    }
}

/** Square side length equals measured height (two-line caption block). */
private class SquareHeightFrameLayout(context: Context) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        if (heightMode != MeasureSpec.UNSPECIFIED && heightSize > 0) {
            val side = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY)
            super.onMeasure(side, heightMeasureSpec)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
