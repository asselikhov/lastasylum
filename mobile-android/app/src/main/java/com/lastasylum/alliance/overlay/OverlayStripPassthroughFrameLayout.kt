package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

/**
 * Корень окна ленты сообщений: касания проходят к игре, кроме зон крестика закрытия.
 * Прямоугольники задаются в координатах содержимого [composeLocatorView] (как [boundsInRoot] в Compose).
 */
internal class OverlayStripPassthroughFrameLayout(context: Context) : FrameLayout(context) {

    /** [ComposeView] ленты — для перевода координат касания в систему Compose. */
    var composeLocatorView: View? = null

    @Volatile
    var dismissRectsInCompose: List<Rect> = emptyList()

    /** true только для жеста, начавшегося по зоне крестика — иначе MOVE/UP не отдаём в Compose (иначе «мёртвая» зона). */
    private var forwardingToCompose = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 1) {
            forwardingToCompose = false
            return false
        }
        val compose = composeLocatorView
        if (compose == null) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                forwardingToCompose = false
                if (dismissRectsInCompose.isEmpty()) {
                    return if (OverlayPassthroughMultitouchFrameLayout.hitVisibleLeafDescendant(this, ev.x, ev.y)) {
                        forwardingToCompose = true
                        super.dispatchTouchEvent(ev)
                    } else {
                        false
                    }
                }
                // [compose] вложен в промежуточный [FrameLayout]; [View.getLeft]/[getTop] — относительно родителя,
                // не этого хоста. Без экранных координат зоны крестика смещаются: DOWN уходит в Compose мимо кнопки,
                // жест не доходит до игре — остаётся «мёртвая» полоса после закрытия карточек.
                val loc = IntArray(2)
                compose.getLocationOnScreen(loc)
                val lx = (ev.rawX - loc[0]).toInt()
                val ly = (ev.rawY - loc[1]).toInt()
                val hitSlopPx = (8 * resources.displayMetrics.density).toInt()
                if (dismissRectsInCompose.none { rect ->
                    if (rect.isEmpty()) return@none false
                    val expanded = Rect(
                        rect.left - hitSlopPx,
                        rect.top - hitSlopPx,
                        rect.right + hitSlopPx,
                        rect.bottom + hitSlopPx,
                    )
                    expanded.contains(lx, ly)
                }) return false
                forwardingToCompose = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!forwardingToCompose) return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!forwardingToCompose) return false
            }
            else -> {
                if (!forwardingToCompose) return false
            }
        }
        val handled = super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP ||
            ev.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            forwardingToCompose = false
        }
        return handled
    }
}
