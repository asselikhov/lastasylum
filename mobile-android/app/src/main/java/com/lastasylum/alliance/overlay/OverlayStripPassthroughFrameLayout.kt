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

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 1) return false
        val compose = composeLocatorView
        if (compose == null) return false
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            if (dismissRectsInCompose.isEmpty()) return false
            // [compose] вложен в промежуточный [FrameLayout]; [View.getLeft]/[getTop] — относительно родителя,
            // не этого хоста. Без экранных координат зоны крестика смещаются: DOWN уходит в Compose мимо кнопки,
            // жест не доходит до игры — остаётся «мёртвая» полоса после закрытия карточек.
            val loc = IntArray(2)
            compose.getLocationOnScreen(loc)
            val lx = (ev.rawX - loc[0]).toInt()
            val ly = (ev.rawY - loc[1]).toInt()
            if (dismissRectsInCompose.none { !it.isEmpty && it.contains(lx, ly) }) return false
        }
        return super.dispatchTouchEvent(ev)
    }
}
