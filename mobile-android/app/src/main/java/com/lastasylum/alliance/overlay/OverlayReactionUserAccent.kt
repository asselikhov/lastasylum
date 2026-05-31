package com.lastasylum.alliance.overlay

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import kotlin.math.abs

internal object OverlayReactionUserAccent {
    fun ringColorFor(userId: String): Int {
        val hash = abs(userId.trim().hashCode())
        val hue = (hash % 360).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.92f))
    }

    fun strokeDrawable(
        color: Int,
        cornerPx: Float,
        strokePx: Int,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = cornerPx
        setStroke(strokePx.coerceAtLeast(1), color)
        setColor(Color.TRANSPARENT)
    }
}
