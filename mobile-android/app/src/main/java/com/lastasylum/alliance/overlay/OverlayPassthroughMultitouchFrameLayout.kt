package com.lastasylum.alliance.overlay

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Корень overlay-окна:
 * - при pinch (>1 пальца) не участвуем в dispatch;
 * - при [MotionEvent.ACTION_DOWN] в «пустой» зоне (нет видимого листового потомка под точкой) —
 *   не забираем жест (важно для свёрнутой панели и прозорых промежутков в ленте).
 */
internal class OverlayPassthroughMultitouchFrameLayout(context: Context) : FrameLayout(context) {

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 1) return false
        if (ev.actionMasked == MotionEvent.ACTION_DOWN &&
            !hitVisibleLeafDescendant(this, ev.x, ev.y)
        ) {
            return false
        }
        return super.dispatchTouchEvent(ev)
    }

    private companion object {
        /**
         * Есть ли под (x,y) в координатах [parent] видимый не-[ViewGroup] view или [ViewGroup] с попаданием во внутреннего.
         */
        fun hitVisibleLeafDescendant(parent: ViewGroup, x: Float, y: Float): Boolean {
            for (i in parent.childCount - 1 downTo 0) {
                val child = parent.getChildAt(i)
                if (child.visibility != View.VISIBLE) continue
                val cx = x - child.left
                val cy = y - child.top
                if (cx < 0 || cy < 0 || cx >= child.width || cy >= child.height) continue
                when (child) {
                    is ViewGroup -> {
                        if (hitVisibleLeafDescendant(child, cx, cy)) return true
                    }
                    else -> return true
                }
            }
            return false
        }
    }
}
