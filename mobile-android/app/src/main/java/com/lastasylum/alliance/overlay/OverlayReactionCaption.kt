package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import com.lastasylum.alliance.R

internal object OverlayReactionCaption {
    private const val NICK_SP = 12.5f
    private const val BADGE_SIZE_DP = 8
    private const val AVATAR_SIZE_DP = 22
    private const val MERGE_CHIP_MIN_DP = 18

    fun createSenderRow(
        context: Context,
        displayName: String,
        fromUserId: String,
        broadcast: Boolean,
        maxWidthPx: Int,
        dp: (Int) -> Int,
        mergeCount: Int = 1,
    ): LinearLayout {
        val nick = formatNick(context, displayName)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
            disableOverlayTouchTarget(this)
            setTag(R.id.tag_overlay_reaction_caption, true)
        }
        row.addView(OverlayReactionSenderAvatar.create(context, displayName, fromUserId, dp))
        val nameView = TextView(context).apply {
            text = nick
            setTextColor(Color.parseColor("#E8F0FFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, NICK_SP)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = (maxWidthPx - dp(AVATAR_SIZE_DP) - dp(BADGE_SIZE_DP) - dp(MERGE_CHIP_MIN_DP) - dp(28))
                .coerceAtLeast(dp(48))
            setShadowLayer(4f, 0f, 1f, Color.parseColor("#88000000"))
            disableOverlayTouchTarget(this)
        }
        val badge = ImageView(context).apply {
            val size = dp(BADGE_SIZE_DP)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginStart = dp(4) }
            setImageDrawable(
                AppCompatResources.getDrawable(
                    context,
                    if (broadcast) {
                        R.drawable.ic_overlay_reaction_badge_broadcast
                    } else {
                        R.drawable.ic_overlay_reaction_badge_private
                    },
                ),
            )
            contentDescription = context.getString(
                if (broadcast) {
                    R.string.overlay_reaction_burst_badge_broadcast
                } else {
                    R.string.overlay_reaction_burst_badge_private
                },
            )
            disableOverlayTouchTarget(this)
        }
        val mergeChip = TextView(context).apply {
            id = R.id.tag_overlay_reaction_merge_count
            setTextColor(Color.parseColor("#E8F0FFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(1), dp(4), dp(1))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#50121824"))
                cornerRadius = dp(6).toFloat()
            }
            disableOverlayTouchTarget(this)
            visibility = View.GONE
        }
        row.addView(nameView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(badge)
        row.addView(mergeChip)
        updateMergeCount(row, mergeCount)
        return row
    }

    fun updateMergeCount(row: LinearLayout, count: Int) {
        val chip = row.findViewById<TextView>(R.id.tag_overlay_reaction_merge_count) ?: return
        if (count <= 1) {
            chip.visibility = View.GONE
            return
        }
        chip.visibility = View.VISIBLE
        chip.text = row.context.getString(R.string.overlay_reaction_burst_merge_count, count)
    }

    private fun formatNick(context: Context, displayName: String): String {
        val trimmed = displayName.trim().ifBlank { "—" }
        if (trimmed.startsWith("@")) return trimmed
        return context.getString(R.string.overlay_reaction_burst_sender_at, trimmed)
    }
}
