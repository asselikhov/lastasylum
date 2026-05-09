package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Корень окна ленты сообщений: касания проходят к игре, кроме явных зон (крестики закрытия),
 * заданных в экранных координатах ([dismissScreenRects]).
 */
internal class OverlayStripPassthroughFrameLayout(context: Context) : FrameLayout(context) {

    @Volatile
    var dismissScreenRects: List<Rect> = emptyList()

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 1) return false
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            val rx = ev.rawX.toInt()
            val ry = ev.rawY.toInt()
            if (dismissScreenRects.none { it.contains(rx, ry) }) return false
        }
        return super.dispatchTouchEvent(ev)
    }
}
