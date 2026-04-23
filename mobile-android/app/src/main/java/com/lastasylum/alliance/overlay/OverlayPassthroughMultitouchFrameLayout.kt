package com.lastasylum.alliance.overlay

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Корень overlay-окна: при pinch / мультикасании не участвуем в dispatch,
 * чтобы второй палец и жесты масштаба доходили до игры под оверлеем.
 */
internal class OverlayPassthroughMultitouchFrameLayout(context: Context) : FrameLayout(context) {

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 1) return false
        return super.dispatchTouchEvent(ev)
    }
}
