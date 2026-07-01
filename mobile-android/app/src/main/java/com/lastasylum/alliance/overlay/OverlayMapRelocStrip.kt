package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.lastasylum.alliance.R
import com.lastasylum.alliance.game.MapRelocPanelTarget

/**
 * Кнопка «Маршрут» рядом с игровой «Перемещение» при тапе по пустой клетке карты.
 */
class OverlayMapRelocStrip(
    private val context: Context,
    private val mainHandler: Handler,
    private val dp: (Int) -> Int,
    private val onRouteClick: (target: MapRelocPanelTarget) -> Unit,
) {
    private var root: LinearLayout? = null
    private var target: MapRelocPanelTarget? = null
    private var attached = false
    private var attachedWindowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    val isShowing: Boolean get() = attached

    fun show(windowManager: WindowManager, panelTarget: MapRelocPanelTarget) {
        runOnMain {
            this.target = panelTarget
            val view = root ?: buildView().also { root = it }
            if (!attached) {
                val params = buildParams().also { layoutParams = it }
                params.y = computeY(panelTarget.panelBottomPx)
                runCatching { windowManager.addView(view, params) }
                    .onSuccess {
                        attached = true
                        attachedWindowManager = windowManager
                    }
                    .onFailure { e -> Log.w(TAG, "addView failed", e) }
            } else {
                applyPosition(panelTarget.panelBottomPx)
            }
        }
    }

    fun hide(windowManager: WindowManager) {
        runOnMain {
            val view = root ?: return@runOnMain
            if (!attached) return@runOnMain
            val mgr = attachedWindowManager ?: windowManager
            runCatching { mgr.removeView(view) }
                .onSuccess {
                    attached = false
                    attachedWindowManager = null
                }
                .onFailure { e -> Log.w(TAG, "hide failed", e) }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == mainHandler.looper) block() else mainHandler.post(block)
    }

    private fun buildParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            OverlayWindowLayout.popupWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = dp(72)
        }
    }

    private fun computeY(panelBottomPx: Int?): Int {
        val screenH = context.resources.displayMetrics.heightPixels
        val fromPanel = panelBottomPx?.let { screenH - it + dp(8) } ?: dp(120)
        return fromPanel.coerceAtLeast(dp(80))
    }

    private fun applyPosition(panelBottomPx: Int?) {
        val view = root ?: return
        val wm = attachedWindowManager ?: return
        val params = layoutParams ?: return
        params.y = computeY(panelBottomPx)
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun buildView(): LinearLayout {
        val routeBtn = TextView(context).apply {
            text = context.getString(R.string.overlay_route_map_button)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            minimumHeight = dp(42)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#FF2563EB"), Color.parseColor("#FF4F46E5")),
            ).apply {
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.parseColor("#55FFFFFF"))
            }
            setOnClickListener {
                val t = target ?: return@setOnClickListener
                onRouteClick(t)
            }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(routeBtn, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    companion object {
        private const val TAG = "OverlayMapRelocStrip"
    }
}
