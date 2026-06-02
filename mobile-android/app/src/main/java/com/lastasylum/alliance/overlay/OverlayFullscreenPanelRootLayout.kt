package com.lastasylum.alliance.overlay

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.graphics.Insets

/**
 * Корень полноэкранной панели оверлея (чат / уведомления / команда).
 *
 * Окно рисуется edge-to-edge, но касания в полосе [navigationBars] не забираем —
 * иначе при жесте «показать навбар» кнопки Back/Home/Recents не получают тапы.
 */
internal class OverlayFullscreenPanelRootLayout(context: Context) : FrameLayout(context) {

    @Volatile
    private var navigationBarInsetBottomPx: Int = 0

    fun updateNavigationBarInsets(insets: Insets) {
        navigationBarInsetBottomPx = insets.bottom.coerceAtLeast(0)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 1) return false
        if (shouldPassTouchToSystem(ev.y)) return false
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 1) return false
        if (shouldPassTouchToSystem(event.y)) return false
        return super.onTouchEvent(event)
    }

    private fun shouldPassTouchToSystem(y: Float): Boolean {
        val inset = navigationBarInsetBottomPx
        if (inset <= 0) return false
        val h = height
        if (h <= 0) return false
        return y >= h - inset
    }
}
