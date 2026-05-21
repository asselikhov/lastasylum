package com.lastasylum.alliance.overlay

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ComposeView

/**
 * Корень overlay-окна:
 * - при pinch (>1 пальца) не участвуем в dispatch;
 * - при [MotionEvent.ACTION_DOWN] в «пустой» зоне (нет интерактивного потомка под точкой) —
 *   не забираем жест (важно для свёрнутой панели и прозорых промежутков в ленте).
 *
 * [ComposeView] не имеет классических leaf-потомков View — hit-test идёт внутри Compose,
 * поэтому для него достаточно попадания в bounds.
 */
internal class OverlayPassthroughMultitouchFrameLayout(context: Context) : FrameLayout(context) {

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 1) return false
        if (!hitInteractiveDescendant(this, ev.x, ev.y)) return false
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 1) return false
        val x = event.x
        val y = event.y
        if (!hitInteractiveDescendant(this, x, y)) return false
        super.onTouchEvent(event)
        // Android 15+: тап в bounds Compose не должен уходить в игру под оверлеем.
        return true
    }

    companion object {
        /**
         * Есть ли под (x,y) в координатах [parent] видимый интерактивный потомок.
         * @see hitVisibleLeafDescendant — alias for strip dismiss fallback.
         */
        fun hitVisibleLeafDescendant(parent: ViewGroup, x: Float, y: Float): Boolean =
            hitInteractiveDescendant(parent, x, y)

        fun hitInteractiveDescendant(parent: ViewGroup, x: Float, y: Float): Boolean {
            if (hitsComposeDescendant(parent, x, y)) return true
            for (i in parent.childCount - 1 downTo 0) {
                val child = parent.getChildAt(i)
                if (child.visibility != View.VISIBLE) continue
                val cx = x - child.left
                val cy = y - child.top
                if (cx < 0 || cy < 0 || cx >= child.width || cy >= child.height) continue
                when (child) {
                    is ComposeView, is AbstractComposeView -> return true
                    is ViewGroup -> {
                        if (hitInteractiveDescendant(child, cx, cy)) return true
                    }
                    else -> {
                        if (child.isClickable || child.isFocusable || child.hasOnClickListeners()) {
                            return true
                        }
                    }
                }
            }
            return false
        }

        private fun hitsComposeDescendant(parent: ViewGroup, x: Float, y: Float): Boolean {
            for (i in parent.childCount - 1 downTo 0) {
                val child = parent.getChildAt(i)
                if (child.visibility != View.VISIBLE) continue
                val cx = x - child.left
                val cy = y - child.top
                when (child) {
                    is ComposeView, is AbstractComposeView -> {
                        if (cx < 0 || cy < 0) continue
                        val w = child.width.coerceAtLeast(child.measuredWidth)
                        val h = child.height.coerceAtLeast(child.measuredHeight)
                        if (w <= 0 || h <= 0 || (cx < w && cy < h)) return true
                    }
                    is ViewGroup -> {
                        if (hitsComposeDescendant(child, cx, cy)) return true
                    }
                }
            }
            return false
        }
    }
}
