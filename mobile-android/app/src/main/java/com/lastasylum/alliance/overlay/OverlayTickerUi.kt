package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.TextView

object OverlayTickerUi {
    fun applyTickerStyle(context: Context, view: TextView) {
        val card = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 12f)
            setColor(Color.parseColor("#CC111826"))
            setStroke(dp(context, 1.25f).toInt(), Color.parseColor("#667E8CA8"))
        }

        view.background = card
        view.setTextColor(Color.parseColor("#FFF1F5FF"))
        view.setPadding(
            dp(context, 12f).toInt(),
            dp(context, 9f).toInt(),
            dp(context, 12f).toInt(),
            dp(context, 9f).toInt(),
        )
        view.maxLines = 3
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        view.letterSpacing = 0.01f
    }

    fun applyToggleChipStyle(context: Context, view: TextView) {
        val chip = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 18f)
            setColor(Color.parseColor("#C21A2233"))
            setStroke(dp(context, 1f).toInt(), Color.parseColor("#6D8AA0C2"))
        }
        view.background = chip
        view.setTextColor(Color.parseColor("#FFEAF0FF"))
        view.setPadding(
            dp(context, 12f).toInt(),
            dp(context, 6f).toInt(),
            dp(context, 12f).toInt(),
            dp(context, 6f).toInt(),
        )
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        view.letterSpacing = 0.02f
        view.alpha = 0.92f
    }

    private fun dp(context: Context, value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics,
        )
    }
}
