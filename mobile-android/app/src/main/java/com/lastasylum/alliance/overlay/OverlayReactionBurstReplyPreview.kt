package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionBurstReplyTo
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibility

internal object OverlayReactionBurstReplyPreview {
    fun attachReplyRow(
        column: LinearLayout,
        context: Context,
        replyTo: OverlayReactionBurstReplyTo,
        visualFactory: OverlayReactionVisualFactory,
        dp: (Int) -> Int,
        hero: Boolean,
    ) {
        val previewPx = dp(if (hero) 36 else 28)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val label = TextView(context).apply {
            text = context.getString(R.string.overlay_notifications_reply_scope)
            setTextColor(Color.parseColor("#99A8B5C8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (hero) 11f else 9f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            maxLines = 1
        }
        row.addView(
            label,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val gap = dp(6)
        val previewHost = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            background = replyPreviewBackground(context, replyTo.visibility)
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
        row.addView(
            previewHost,
            LinearLayout.LayoutParams(previewPx, previewPx).apply {
                marginStart = gap
            },
        )
        column.addView(
            row,
            0,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(if (hero) 6 else 4)
                gravity = Gravity.CENTER_HORIZONTAL
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
