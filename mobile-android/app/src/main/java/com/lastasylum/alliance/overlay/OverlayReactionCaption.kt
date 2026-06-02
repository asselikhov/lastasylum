package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.lastasylum.alliance.R

internal data class OverlayReactionHeroCaptionBlock(
    val root: LinearLayout,
    val textColumn: LinearLayout,
    val scopeRow: LinearLayout,
    val scopeLabelView: TextView,
    val fromLineView: TextView,
)

internal object OverlayReactionCaption {
    private const val LINE_SP = 14f
    private const val REPLY_LINE_GAP_DP = 3f
    private val NickColor = Color.parseColor("#FFF4F7FF")
    private val ReplyScopeColor = Color.parseColor("#FF7EB8FF")
    private val PersonalScopeColor = Color.parseColor("#FF9070B8")
    private val BroadcastScopeColor = Color.parseColor("#FF50B860")

    private fun nickBackground(context: Context, cornerDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(OverlayReactionBurstLayout.CAPTION_BACKGROUND_HEX))
            cornerRadius = context.resources.displayMetrics.density * cornerDp
        }

    fun formatReactionDisplayNick(displayName: String): String =
        OverlayReactionNickFormat.format(displayName)

    private fun scopeLabel(context: Context, broadcast: Boolean, isReply: Boolean): String =
        when {
            isReply -> context.getString(R.string.overlay_notifications_reply_scope)
            broadcast -> context.getString(R.string.overlay_reaction_burst_caption_broadcast)
            else -> context.getString(R.string.overlay_reaction_burst_caption_private)
        }

    private fun scopeColor(broadcast: Boolean, isReply: Boolean): Int =
        when {
            isReply -> ReplyScopeColor
            broadcast -> BroadcastScopeColor
            else -> PersonalScopeColor
        }

    private fun scopeLineText(scope: String, mergeCount: Int, mergeSuffix: String): String =
        if (mergeCount > 1) {
            "[$scope]$mergeSuffix"
        } else {
            "[$scope]"
        }

    fun createHeroCaptionBlock(
        context: Context,
        displayName: String,
        broadcast: Boolean,
        isReply: Boolean = false,
        mergeCount: Int = 1,
    ): OverlayReactionHeroCaptionBlock {
        val nick = formatReactionDisplayNick(displayName)
        val scope = scopeLabel(context, broadcast, isReply)
        val mergeSuffix = if (mergeCount > 1) {
            " ${context.getString(R.string.overlay_reaction_burst_merge_count, mergeCount)}"
        } else {
            ""
        }
        val density = context.resources.displayMetrics.density
        val padH = (density * 8).toInt()
        val padV = (density * 4).toInt()

        val scopeLabelView = TextView(context).apply {
            text = scopeLineText(scope, mergeCount, mergeSuffix)
            setTextColor(scopeColor(broadcast, isReply))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LINE_SP)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            if (isReply) {
                maxLines = 2
                ellipsize = null
            } else {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            setShadowLayer(6f, 0f, 2f, Color.parseColor("#CC000000"))
            setTag(R.id.tag_overlay_reaction_caption_scope, true)
            disableOverlayTouchTarget(this)
        }

        val scopeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            addView(
                scopeLabelView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val fromLineView = TextView(context).apply {
            text = context.getString(R.string.overlay_reaction_burst_from, nick)
            setTextColor(NickColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LINE_SP)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            if (isReply) {
                maxLines = 2
                ellipsize = null
            } else {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            setShadowLayer(6f, 0f, 2f, Color.parseColor("#CC000000"))
            disableOverlayTouchTarget(this)
        }

        val lineGapPx = if (isReply) {
            (density * REPLY_LINE_GAP_DP).toInt()
        } else {
            (density * 2).toInt()
        }
        val textLineWidth = if (isReply) {
            LinearLayout.LayoutParams.WRAP_CONTENT
        } else {
            LinearLayout.LayoutParams.MATCH_PARENT
        }
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            addView(
                scopeRow,
                LinearLayout.LayoutParams(
                    textLineWidth,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                fromLineView,
                LinearLayout.LayoutParams(
                    textLineWidth,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = lineGapPx
                },
            )
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(padH, padV, padH, padV)
            background = nickBackground(context, cornerDp = 6f)
            setTag(R.id.tag_overlay_reaction_caption, true)
            addView(
                textColumn,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        return OverlayReactionHeroCaptionBlock(
            root = root,
            textColumn = textColumn,
            scopeRow = scopeRow,
            scopeLabelView = scopeLabelView,
            fromLineView = fromLineView,
        )
    }

    private fun findFromLineView(captionRoot: View, scopeView: TextView): TextView? {
        if (captionRoot is TextView && captionRoot !== scopeView) {
            return captionRoot
        }
        if (captionRoot is ViewGroup) {
            for (i in 0 until captionRoot.childCount) {
                findFromLineView(captionRoot.getChildAt(i), scopeView)?.let { return it }
            }
        }
        return null
    }

    private fun findScopeLabelView(captionRoot: View): TextView? {
        if (captionRoot is TextView && captionRoot.getTag(R.id.tag_overlay_reaction_caption_scope) == true) {
            return captionRoot
        }
        if (captionRoot is ViewGroup) {
            for (i in 0 until captionRoot.childCount) {
                findScopeLabelView(captionRoot.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    fun updateMergeCount(
        captionRoot: View,
        displayName: String,
        broadcast: Boolean,
        isReply: Boolean,
        mergeCount: Int,
    ) {
        val scopeView = findScopeLabelView(captionRoot) ?: return
        val context = captionRoot.context
        val scope = scopeLabel(context, broadcast, isReply)
        val mergeSuffix = if (mergeCount > 1) {
            " ${context.getString(R.string.overlay_reaction_burst_merge_count, mergeCount)}"
        } else {
            ""
        }
        scopeView.text = scopeLineText(scope, mergeCount, mergeSuffix)
        scopeView.setTextColor(scopeColor(broadcast, isReply))

        val nick = formatReactionDisplayNick(displayName)
        findFromLineView(captionRoot, scopeView)?.text =
            context.getString(R.string.overlay_reaction_burst_from, nick)
    }

    fun miniContentDescription(
        context: Context,
        displayName: String,
        broadcast: Boolean,
        isReply: Boolean = false,
    ): String {
        val nick = formatReactionDisplayNick(displayName)
        val scope = scopeLabel(context, broadcast, isReply)
        val fromLine = context.getString(R.string.overlay_reaction_burst_from, nick)
        return "[$scope], $fromLine"
    }

    fun createMiniNick(
        context: Context,
        displayName: String,
        broadcast: Boolean,
        isReply: Boolean = false,
    ): TextView {
        val nick = formatReactionDisplayNick(displayName)
        val scope = scopeLabel(context, broadcast, isReply)
        return TextView(context).apply {
            text = nick
            contentDescription = context.getString(R.string.overlay_reaction_mini_tooltip, nick, scope)
            setTextColor(PersonalScopeColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setShadowLayer(4f, 0f, 1f, Color.parseColor("#99000000"))
            setPadding(
                (context.resources.displayMetrics.density * 4).toInt(),
                (context.resources.displayMetrics.density * 2).toInt(),
                (context.resources.displayMetrics.density * 4).toInt(),
                (context.resources.displayMetrics.density * 2).toInt(),
            )
            background = nickBackground(context, cornerDp = 4f)
            disableOverlayTouchTarget(this)
        }
    }
}
