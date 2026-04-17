package com.lastasylum.alliance.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView

object OverlayTickerUi {
    fun applyTickerStyle(context: Context, view: TextView) {
        val card = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 12f)
            setColor(Color.parseColor("#CC10141E"))
            setStroke(dp(context, 1.25f).toInt(), Color.parseColor("#559B7CFF"))
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
            setColor(Color.parseColor("#CC1A1F2B"))
            setStroke(dp(context, 1f).toInt(), Color.parseColor("#449B7CFF"))
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

    /** Круглая кнопка оверлея (показать/скрыть UI, блокировка позиций). */
    fun applyRoundOverlayFab(context: Context, button: ImageButton) {
        val side = dp(context, 44f).toInt()
        val pad = dp(context, 10f).toInt()
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#E6282540"))
            setStroke(dp(context, 1.5f).toInt(), Color.parseColor("#889B7CFF"))
        }
        button.background = circle
        button.minimumWidth = side
        button.minimumHeight = side
        button.setPadding(pad, pad, pad, pad)
        button.scaleType = ImageView.ScaleType.CENTER_INSIDE
        button.imageTintList = ColorStateList.valueOf(Color.parseColor("#F0ECFF"))
        button.alpha = 0.96f
    }

    private fun dp(context: Context, value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics,
        )
    }
}
