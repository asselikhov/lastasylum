package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionBurstReplyTo
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibility

internal object OverlayReactionBurstReplyPreview {
    /** Fixed square beside reply caption (incoming burst hero). */
    private const val HERO_PREVIEW_SIDE_DP = 32
    private const val MINI_PREVIEW_SIDE_DP = 28
    private const val PREVIEW_INSET_DP = 4
    private const val TEXT_PREVIEW_GAP_DP = 8

    /** Parent reaction thumbnail to the right of scope + from lines. */
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

        val previewSidePx = dp(if (hero) HERO_PREVIEW_SIDE_DP else MINI_PREVIEW_SIDE_DP)
        val insetPx = dp(PREVIEW_INSET_DP)
        val contentPx = (previewSidePx - insetPx * 2).coerceAtLeast(dp(18))
        val previewHost = ReplyParentPreviewHost(context, previewSidePx).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(insetPx, insetPx, insetPx, insetPx)
            background = replyPreviewBackground(context, replyTo.visibility)
            contentDescription = context.getString(
                R.string.overlay_reaction_burst_reply_parent_preview,
                replyTo.reaction,
            )
        }
        val reaction = overlayQuickReactionById(context, replyTo.reaction)
        val animView = visualFactory.createAnimView(reaction, contentPx, playLottie = false)
        applyBurstVisualFullOpacity(animView)
        applyParentPreviewFitInside(animView)
        previewHost.addView(
            animView,
            FrameLayout.LayoutParams(contentPx, contentPx, Gravity.CENTER),
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

    /** Ensures raster previews stay uncropped if scale type is overridden elsewhere. */
    private fun applyParentPreviewFitInside(view: View) {
        if (view is ImageView) {
            view.scaleType = ImageView.ScaleType.FIT_CENTER
        }
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

/** Fixed square thumbnail; does not stretch with caption line count. */
private class ReplyParentPreviewHost(
    context: Context,
    private val sidePx: Int,
) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val sideSpec = MeasureSpec.makeMeasureSpec(sidePx, MeasureSpec.EXACTLY)
        super.onMeasure(sideSpec, sideSpec)
    }
}
