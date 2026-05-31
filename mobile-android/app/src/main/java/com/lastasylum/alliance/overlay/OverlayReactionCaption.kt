package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import com.lastasylum.alliance.R

internal object OverlayReactionCaption {
    private const val NICK_SP = 14f
    private val NickColor = Color.parseColor("#FFF4F7FF")
    private val ScopeColor = Color.parseColor("#B0C8D8E8")

    fun formatReactionDisplayNick(displayName: String): String =
        OverlayReactionNickFormat.format(displayName)

    fun createCaptionLine(
        context: Context,
        displayName: String,
        broadcast: Boolean,
        mergeCount: Int = 1,
    ): TextView {
        val nick = formatReactionDisplayNick(displayName)
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
        val line = "$nick · $scope$mergeSuffix"
        val spannable = SpannableString(line).apply {
            setSpan(
                ForegroundColorSpan(NickColor),
                0,
                nick.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            val scopeStart = nick.length + 3
            if (scopeStart < length) {
                setSpan(
                    ForegroundColorSpan(ScopeColor),
                    scopeStart,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        return TextView(context).apply {
            text = spannable
            setTextColor(NickColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, NICK_SP)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setShadowLayer(6f, 0f, 2f, Color.parseColor("#CC000000"))
            alpha = OverlayReactionBurstLayout.CAPTION_ALPHA
            setPadding(
                (context.resources.displayMetrics.density * 8).toInt(),
                (context.resources.displayMetrics.density * 4).toInt(),
                (context.resources.displayMetrics.density * 8).toInt(),
                (context.resources.displayMetrics.density * 4).toInt(),
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC0D1524"))
                cornerRadius = context.resources.displayMetrics.density * 6f
            }
            disableOverlayTouchTarget(this)
            setTag(R.id.tag_overlay_reaction_caption, true)
        }
    }

    fun updateMergeCount(caption: TextView, displayName: String, broadcast: Boolean, mergeCount: Int) {
        val rebuilt = createCaptionLine(caption.context, displayName, broadcast, mergeCount)
        caption.text = rebuilt.text
    }

    fun miniContentDescription(context: Context, displayName: String, broadcast: Boolean): String {
        val nick = formatReactionDisplayNick(displayName)
        val scope = context.getString(
            if (broadcast) {
                R.string.overlay_reaction_burst_caption_broadcast
            } else {
                R.string.overlay_reaction_burst_caption_private
            },
        )
        return context.getString(R.string.overlay_reaction_burst_caption_line, nick, scope)
    }

    fun createMiniNick(
        context: Context,
        displayName: String,
        broadcast: Boolean,
        maxWidthPx: Int,
    ): TextView {
        val nick = formatReactionDisplayNick(displayName)
        val scope = context.getString(
            if (broadcast) {
                R.string.overlay_reaction_burst_caption_broadcast
            } else {
                R.string.overlay_reaction_burst_caption_private
            },
        )
        return TextView(context).apply {
            text = nick
            contentDescription = context.getString(R.string.overlay_reaction_mini_tooltip, nick, scope)
            setTextColor(ScopeColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = maxWidthPx
            setShadowLayer(4f, 0f, 1f, Color.parseColor("#99000000"))
            alpha = OverlayReactionBurstLayout.CAPTION_ALPHA
            disableOverlayTouchTarget(this)
        }
    }
}
