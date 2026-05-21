package com.lastasylum.alliance.overlay

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Корень компактных HUD-окон (новости / чат / участники).
 *
 * Окно [WRAP_CONTENT] — весь его прямоугольник должен получать касания для [ComposeView].
 * На Android 15+ необработанный тап внутри окна может «пробиваться» в игру; здесь одиночный
 * палец всегда остаётся в оверлее. Pinch (2+ пальца) по-прежнему уходит в игру.
 */
internal class OverlayHudRootLayout(context: Context) : FrameLayout(context) {

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 1) return false
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 1) return false
        super.onTouchEvent(event)
        return true
    }
}
