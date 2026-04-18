package com.lastasylum.alliance.overlay

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import kotlin.math.abs

/**
 * Перетаскивание окон поверх [WindowManager]: порог slop и обработчик тачей для FAB-ов.
 * Вынесено из [CombatOverlayService] для тестируемости и читаемости.
 */
object OverlayWindowDragHelper {
    /** Порог «это перетаскивание, не тап»: чуть ниже системного slop. */
    fun dragSlopPx(context: Context, minDp: Int = 3, maxDp: Int = 12): Int {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        val dm = context.resources.displayMetrics
        fun dp(v: Int): Int = (v * dm.density).toInt().coerceAtLeast(1)
        return (slop * 2 / 3).coerceIn(dp(minDp), dp(maxDp))
    }

    /**
     * @param minHitSizeDp минимальный размер зоны для clamp по экрану
     */
    fun attachDraggableFab(
        context: Context,
        windowManager: WindowManager,
        view: View,
        params: WindowManager.LayoutParams,
        isDragLocked: () -> Boolean,
        onTap: () -> Unit,
        minHitSizeDp: Int = 48,
    ) {
        var initialX = 0
        var initialY = 0
        var startRawX = 0f
        var startRawY = 0f
        var dragging = false
        val threshold = dragSlopPx(context)
        val dm = context.resources.displayMetrics
        fun dp(v: Int): Int = (v * dm.density).toInt().coerceAtLeast(1)

        view.setOnTouchListener { _, event ->
            if (isDragLocked()) {
                when (event.action) {
                    MotionEvent.ACTION_UP -> onTap()
                    else -> Unit
                }
                return@setOnTouchListener true
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    (view as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                    initialX = params.x
                    initialY = params.y
                    startRawX = event.rawX
                    startRawY = event.rawY
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startRawX).toInt()
                    val dy = (event.rawY - startRawY).toInt()
                    if (!dragging &&
                        (abs(dx) > threshold || abs(dy) > threshold)
                    ) {
                        dragging = true
                    }
                    if (dragging) {
                        val sw = dm.widthPixels
                        val sh = dm.heightPixels
                        val w = view.width.coerceAtLeast(dp(minHitSizeDp))
                        val h = view.height.coerceAtLeast(dp(minHitSizeDp))
                        params.x = (initialX + dx).coerceIn(0, (sw - w).coerceAtLeast(0))
                        params.y = (initialY + dy).coerceIn(0, (sh - h).coerceAtLeast(0))
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragging) onTap()
                    dragging = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    true
                }

                else -> false
            }
        }
    }
}
