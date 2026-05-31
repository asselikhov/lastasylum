package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.lastasylum.alliance.R

internal object OverlayReactionCaption {
    private const val NICK_SP = 11f

    fun createCaptionLine(
        context: Context,
        displayName: String,
        broadcast: Boolean,
        maxWidthPx: Int,
        mergeCount: Int = 1,
    ): TextView {
        val nick = formatNick(context, displayName)
        val scope = context.getString(
            if (broadcast) {
                R.string.overlay_reaction_burst_caption_broadcast
            } else {
                R.string.overlay_reaction_burst_caption_private
            },
        )
        val mergeSuffix = if (mergeCount > 1) {
            " ${context.getString(R.string.overlay_reaction_burst_merge_count, mergeCount)}"
        } else {
            ""
        }
        return TextView(context).apply {
            text = context.getString(R.string.overlay_reaction_burst_caption_line, nick, scope) + mergeSuffix
            setTextColor(Color.parseColor("#D9E8F0FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, NICK_SP)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = maxWidthPx
            setShadowLayer(4f, 0f, 1f, Color.parseColor("#88000000"))
            alpha = OverlayReactionBurstLayout.CAPTION_ALPHA
            disableOverlayTouchTarget(this)
            setTag(R.id.tag_overlay_reaction_caption, true)
        }
    }

    fun updateMergeCount(caption: TextView, displayName: String, broadcast: Boolean, mergeCount: Int) {
        val context = caption.context
        val nick = formatNick(context, displayName)
        val scope = context.getString(
            if (broadcast) {
                R.string.overlay_reaction_burst_caption_broadcast
            } else {
                R.string.overlay_reaction_burst_caption_private
            },
        )
        val mergeSuffix = if (mergeCount > 1) {
            " ${context.getString(R.string.overlay_reaction_burst_merge_count, mergeCount)}"
        } else {
            ""
        }
        caption.text = context.getString(R.string.overlay_reaction_burst_caption_line, nick, scope) + mergeSuffix
    }

    fun miniContentDescription(context: Context, displayName: String, broadcast: Boolean): String {
        val nick = formatNick(context, displayName)
        val scope = context.getString(
            if (broadcast) {
                R.string.overlay_reaction_burst_caption_broadcast
            } else {
                R.string.overlay_reaction_burst_caption_private
            },
        )
        return context.getString(R.string.overlay_reaction_burst_caption_line, nick, scope)
    }

    private fun formatNick(context: Context, displayName: String): String {
        val trimmed = displayName.trim().ifBlank { "—" }
        if (trimmed.startsWith("@")) return trimmed
        return context.getString(R.string.overlay_reaction_burst_sender_at, trimmed)
    }
}
