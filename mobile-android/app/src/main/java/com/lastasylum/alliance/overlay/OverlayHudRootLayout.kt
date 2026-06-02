package com.lastasylum.alliance.overlay

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Корень компактных HUD-окон (левый статус / правый голос).
 *
 * Hit-test делегируется [ComposeView]: пустая зона (например minWidth 280dp у правого HUD
 * без кнопок слева) не перехватывает тап — касание уходит в окно под ним (кнопка обновления).
 * Pinch (2+ пальца) по-прежнему уходит в игру.
 */
internal class OverlayHudRootLayout(context: Context) : FrameLayout(context) {

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 1) return false
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 1) return false
        return false
    }
}
