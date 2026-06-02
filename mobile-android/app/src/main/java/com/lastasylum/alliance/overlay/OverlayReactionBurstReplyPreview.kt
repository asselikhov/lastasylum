package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionBurstReplyTo
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibility

internal object OverlayReactionBurstReplyPreview {
    private const val HERO_PREVIEW_MIN_DP = 44
    private const val MINI_PREVIEW_MIN_DP = 28
    private const val TEXT_PREVIEW_GAP_DP = 8

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

        val previewMinPx = dp(if (hero) HERO_PREVIEW_MIN_DP else MINI_PREVIEW_MIN_DP)
        val previewHost = ReplyParentPreviewHost(context, previewMinPx).apply {
            clipChildren = false
            clipToPadding = false
            background = replyPreviewBackground(context, replyTo.visibility)
            contentDescription = context.getString(
                R.string.overlay_reaction_burst_reply_parent_preview,
                replyTo.reaction,
            )
        }
        val reaction = overlayQuickReactionById(context, replyTo.reaction)
        val animView = visualFactory.createAnimView(reaction, previewMinPx, playLottie = false)
        applyBurstVisualFullOpacity(animView)
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
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(
            previewHost,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = dp(TEXT_PREVIEW_GAP_DP)
            },
        )
    }

    private fun replyPreviewBackground(
        context: Context,
        visibility: OverlayReactionLogVisibility,
    ): android.graphics.drawable.GradientDrawable {
        val borderColor = when (visibility) {
            OverlayReactionLogVisibility.Personal -> Color.parseColor("#995870B8")
            OverlayReactionLogVisibility.Broadcast -> Color.parseColor("#9950B860")
        }
        val radius = context.resources.displayMetrics.density * 8f
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor("#E6141C28"))
            cornerRadius = radius
            setStroke((context.resources.displayMetrics.density * 1.25f).toInt().coerceAtLeast(1), borderColor)
        }
    }
}

/** Square preview at least [minSidePx]; grows with two-line caption height when taller. */
private class ReplyParentPreviewHost(
    context: Context,
    private val minSidePx: Int,
) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val side = when {
            heightMode != MeasureSpec.UNSPECIFIED && heightSize > 0 ->
                maxOf(heightSize, minSidePx)
            else -> minSidePx
        }
        val sideSpec = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY)
        super.onMeasure(sideSpec, sideSpec)
    }
}
