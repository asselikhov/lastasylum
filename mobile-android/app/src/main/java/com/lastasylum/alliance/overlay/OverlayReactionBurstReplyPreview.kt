package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionBurstReplyTo
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibility

internal object OverlayReactionBurstReplyPreview {
    /** Parent reaction thumbnail beside scope label on reply bursts. */
    fun attachBesideScopeRow(
        scopeRow: LinearLayout,
        context: Context,
        replyTo: OverlayReactionBurstReplyTo,
        visualFactory: OverlayReactionVisualFactory,
        dp: (Int) -> Int,
        hero: Boolean,
    ) {
        val previewPx = dp(if (hero) 32 else 24)
        val previewHost = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            background = replyPreviewBackground(context, replyTo.visibility)
            contentDescription = context.getString(
                R.string.overlay_reaction_burst_reply_parent_preview,
                replyTo.reaction,
            )
        }
        val reaction = overlayQuickReactionById(context, replyTo.reaction)
        val animView = visualFactory.createAnimView(reaction, previewPx, playLottie = false)
        previewHost.addView(
            animView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ),
        )
        scopeRow.addView(
            previewHost,
            LinearLayout.LayoutParams(previewPx, previewPx).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = dp(6)
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
            setColor(Color.parseColor("#FF141C28"))
            cornerRadius = radius
            setStroke((context.resources.displayMetrics.density).toInt().coerceAtLeast(1), borderColor)
        }
    }
}
