package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.widget.TextView
import androidx.core.graphics.ColorUtils

object OverlayBubbleUi {
    enum class BubbleState {
        IDLE,
        RECORDING,
        SENDING,
        ERROR,
    }

    fun applyBubbleStyle(context: Context, view: TextView, state: BubbleState) {
        applyCircleStyle(context, view, state, sizeDp = 56f, textSp = 11f)
    }

    fun applyQuickCommandStyle(context: Context, view: TextView, state: BubbleState) {
        applyCircleStyle(context, view, state, sizeDp = 44f, textSp = 10f)
    }

    private fun applyCircleStyle(
        context: Context,
        view: TextView,
        state: BubbleState,
        sizeDp: Float,
        textSp: Float,
    ) {
        val idleFill = Color.parseColor("#2A1F45")
        val idleStroke = Color.parseColor("#8E6CFF")
        val recFill = Color.parseColor("#3D1020")
        val recStroke = Color.parseColor("#FF6464")
        val sendFill = Color.parseColor("#0F2A28")
        val sendStroke = Color.parseColor("#2DD4BF")
        val errFill = Color.parseColor("#2A1515")
        val errStroke = Color.parseColor("#FF4444")

        val (fill, stroke) = when (state) {
            BubbleState.IDLE -> idleFill to idleStroke
            BubbleState.RECORDING -> recFill to recStroke
            BubbleState.SENDING -> sendFill to sendStroke
            BubbleState.ERROR -> errFill to errStroke
        }

        val sizePx = dpToPx(context, sizeDp).toInt()
        val strokePx = dpToPx(context, 2f).toInt()

        val fillDrawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                fill,
                ColorUtils.blendARGB(fill, Color.BLACK, 0.25f),
            ),
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(strokePx, stroke)
        }

        val glow = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                ColorUtils.setAlphaComponent(stroke, 90),
                ColorUtils.setAlphaComponent(stroke, 0),
            ),
        ).apply {
            shape = GradientDrawable.OVAL
        }

        val layer = LayerDrawable(arrayOf(glow, fillDrawable))
        layer.setLayerInset(0, -strokePx * 2, -strokePx * 2, -strokePx * 2, -strokePx * 2)
        layer.setLayerInset(1, strokePx, strokePx, strokePx, strokePx)

        view.background = layer
        view.setTextColor(Color.WHITE)
        view.minWidth = sizePx
        view.minHeight = sizePx
        view.gravity = android.view.Gravity.CENTER
        view.setPadding(0, 0, 0, 0)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
        view.letterSpacing = 0.08f
    }

    private fun dpToPx(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics,
        )
    }
}
