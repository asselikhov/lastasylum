package com.lastasylum.alliance.overlay

import android.content.Context
import android.view.MotionEvent
import android.widget.ScrollView

/**
 * Лента чата в отдельном overlay-окне: обычный [ScrollView] при коротком контенте всё равно
 * участвует в touch pipeline и может «съедать» вертикальные свайпы по игре в полосе ленты.
 * Если контент ниже viewport — не перехватываем и не обрабатываем жесты здесь.
 */
internal class OverlayStripScrollView(context: Context) : ScrollView(context) {

    private fun contentTallerThanViewport(): Boolean {
        val child = getChildAt(0) ?: return false
        if (child.width <= 0 || child.height <= 0) return false
        val viewport = height - paddingTop - paddingBottom
        return child.height > viewport
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 1) return false
        if (!contentTallerThanViewport()) return false
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 1) return false
        if (!contentTallerThanViewport()) return false
        return super.onTouchEvent(ev)
    }
}
