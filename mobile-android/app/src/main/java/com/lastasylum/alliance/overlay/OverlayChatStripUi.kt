package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

object OverlayChatStripUi {
    private val palette: List<Pair<Int, Int>> = listOf(
        Color.parseColor("#FFD54F") to Color.parseColor("#E8EAEF"),
        Color.parseColor("#D4A5FF") to Color.parseColor("#E8DFFF"),
        Color.parseColor("#82B1FF") to Color.parseColor("#DCE7FF"),
        Color.parseColor("#9B7CFF") to Color.parseColor("#DDD4FF"),
        Color.parseColor("#2DD4BF") to Color.parseColor("#B5FFF0"),
        Color.parseColor("#FFB74D") to Color.parseColor("#FFE0B2"),
    )
    private val selfNameColor = Color.parseColor("#C4B5FD")
    private val selfTextColor = Color.parseColor("#E8DFFF")

    fun styleStripScroll(context: Context, scroll: ScrollView) {
        scroll.isVerticalScrollBarEnabled = true
        scroll.isScrollbarFadingEnabled = false
        scroll.setPadding(
            dp(context, 6f).toInt(),
            dp(context, 6f).toInt(),
            dp(context, 6f).toInt(),
            dp(context, 6f).toInt(),
        )
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 14f)
            setColor(Color.parseColor("#E610141E"))
            setStroke(dp(context, 1f).toInt(), Color.parseColor("#559B7CFF"))
        }
        scroll.background = bg
    }

    fun createLinesContainer(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(
                dp(context, 4f).toInt(),
                dp(context, 2f).toInt(),
                dp(context, 4f).toInt(),
                dp(context, 2f).toInt(),
            )
        }
    }

    fun addLine(
        context: Context,
        container: LinearLayout,
        sender: String,
        text: String,
        senderId: String?,
        selfUserId: String?,
    ) {
        val safe = text.replace("\n", " ").trim()
        val preview = if (safe.length > 80) safe.take(80) + "…" else safe
        val displayName = sender.take(18)
        val (nameColor, bodyColor) = colorsFor(senderId, displayName, selfUserId)

        val accent = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dp(context, 4f).toInt(),
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply { marginEnd = dp(context, 6f).toInt() }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 2f)
                setColor(nameColor)
            }
        }

        val body = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            )
            setPadding(
                dp(context, 8f).toInt(),
                dp(context, 5f).toInt(),
                dp(context, 8f).toInt(),
                dp(context, 5f).toInt(),
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setLineSpacing(dp(context, 2f), 1f)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 10f)
                setColor(Color.parseColor("#E615121E"))
            }
            val bracket = "[$displayName]"
            val full = "$bracket: $preview"
            val ss = SpannableStringBuilder(full)
            val nameStart = 0
            val nameEnd = bracket.length
            ss.setSpan(
                ForegroundColorSpan(nameColor),
                nameStart,
                nameEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            ss.setSpan(StyleSpan(Typeface.BOLD), nameStart, nameEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val msgStart = nameEnd
            ss.setSpan(
                ForegroundColorSpan(bodyColor),
                msgStart,
                full.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            setText(ss, TextView.BufferType.SPANNABLE)
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(context, 5f).toInt()
            }
            addView(accent)
            addView(body)
        }
        container.addView(row)
    }

    fun clearLines(container: LinearLayout) {
        container.removeAllViews()
    }

    private fun colorsFor(
        senderId: String?,
        senderName: String,
        selfUserId: String?,
    ): Pair<Int, Int> {
        if (!selfUserId.isNullOrBlank() && senderId == selfUserId) {
            return selfNameColor to selfTextColor
        }
        val key = when {
            !senderId.isNullOrBlank() -> abs(senderId.hashCode())
            else -> abs(senderName.hashCode())
        }
        return palette[key % palette.size]
    }

    private fun dp(context: Context, value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics,
        )
    }
}
