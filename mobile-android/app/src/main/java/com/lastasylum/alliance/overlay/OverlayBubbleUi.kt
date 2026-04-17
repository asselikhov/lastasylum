package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.ColorUtils

object OverlayBubbleUi {
    enum class BubbleState {
        IDLE,
        RECORDING,
        SENDING,
        ERROR,
    }

    /**
     * @param iconOnly только круг с микрофоном (без подписи) — чуть крупнее для читаемой иконки.
     */
    fun applyBubbleStyle(
        context: Context,
        view: View,
        state: BubbleState,
        compact: Boolean = false,
        iconOnly: Boolean = false,
    ) {
        val sizeDp = when {
            iconOnly && compact -> 52f
            iconOnly && !compact -> 60f
            compact -> 54f
            else -> 72f
        }
        applyCircleStyle(context, view, state, sizeDp = sizeDp)
    }

    fun applyQuickCommandStyle(context: Context, view: View, state: BubbleState) {
        applyCircleStyle(context, view, state, sizeDp = 44f)
    }

    private fun applyCircleStyle(
        context: Context,
        view: View,
        state: BubbleState,
        sizeDp: Float,
    ) {
        // Палитра как в SquadRelay (Compose): primary #9B7CFF, surface тёмный, secondary бирюза на «отправке».
        val idleFill = Color.parseColor("#15101F")
        val idleStroke = Color.parseColor("#9B7CFF")
        val recFill = Color.parseColor("#1F1530")
        val recStroke = Color.parseColor("#C4B5FD")
        val sendFill = Color.parseColor("#0D1E1C")
        val sendStroke = Color.parseColor("#2DD4BF")
        val errFill = Color.parseColor("#2A1010")
        val errStroke = Color.parseColor("#FF6B6B")

        val (fill, stroke) = when (state) {
            BubbleState.IDLE -> idleFill to idleStroke
            BubbleState.RECORDING -> recFill to recStroke
            BubbleState.SENDING -> sendFill to sendStroke
            BubbleState.ERROR -> errFill to errStroke
        }

        val sizePx = dpToPx(context, sizeDp).toInt()
        val strokePx = dpToPx(context, if (state == BubbleState.IDLE || state == BubbleState.RECORDING) 3f else 2f).toInt()

        val fillDrawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                fill,
                ColorUtils.blendARGB(fill, Color.BLACK, 0.28f),
            ),
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(strokePx, stroke)
        }

        val glow = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                ColorUtils.setAlphaComponent(stroke, 110),
                ColorUtils.setAlphaComponent(stroke, 0),
            ),
        ).apply {
            shape = GradientDrawable.OVAL
        }

        val layer = LayerDrawable(arrayOf(glow, fillDrawable))
        layer.setLayerInset(0, -strokePx * 2, -strokePx * 2, -strokePx * 2, -strokePx * 2)
        layer.setLayerInset(1, strokePx, strokePx, strokePx, strokePx)

        view.background = layer
        view.minimumWidth = sizePx
        view.minimumHeight = sizePx
    }

    private fun dpToPx(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics,
        )
    }
}
