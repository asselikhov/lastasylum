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
 * Fallback-кнопка «Маршрут» (оверлей), если внутриигровая NGUI-кнопка не создалась.
 */
class OverlayMapRelocStrip(
    private val context: Context,
    private val mainHandler: Handler,
    private val dp: (Int) -> Int,
    private val onRouteClick: (target: MapRelocPanelTarget) -> Unit,
) {
    private var root: LinearLayout? = null
    private var routeBtn: TextView? = null
    private var target: MapRelocPanelTarget? = null
    private var attached = false
    private var attachedWindowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    val isShowing: Boolean get() = attached

    fun show(windowManager: WindowManager, panelTarget: MapRelocPanelTarget) {
        runOnMain {
            this.target = panelTarget
            val view = root ?: buildView().also { root = it }
            styleRouteButton(panelTarget)
            if (!attached) {
                val params = buildParams(panelTarget).also { layoutParams = it }
                runCatching { windowManager.addView(view, params) }
                    .onSuccess {
                        attached = true
                        attachedWindowManager = windowManager
                    }
                    .onFailure { e -> Log.w(TAG, "addView failed", e) }
            } else {
                applyLayout(panelTarget)
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

    private fun buildParams(panelTarget: MapRelocPanelTarget): WindowManager.LayoutParams {
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
            applyAnchor(this, panelTarget)
        }
    }

    private fun applyLayout(panelTarget: MapRelocPanelTarget) {
        val view = root ?: return
        val wm = attachedWindowManager ?: return
        val params = layoutParams ?: return
        applyAnchor(params, panelTarget)
        styleRouteButton(panelTarget)
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun applyAnchor(params: WindowManager.LayoutParams, panelTarget: MapRelocPanelTarget) {
        val screenW = context.resources.displayMetrics.widthPixels
        val gap = dp(6)
        if (panelTarget.hasRelocButtonAnchor()) {
            val btnW = panelTarget.relocBtnWidthPx ?: dp(96)
            val btnH = panelTarget.relocBtnHeightPx ?: dp(42)
            val left = (panelTarget.relocBtnRightPx ?: 0) + gap
            val top = panelTarget.relocBtnTopPx ?: 0
            params.gravity = Gravity.TOP or Gravity.START
            params.x = left.coerceIn(0, (screenW - btnW - dp(4)).coerceAtLeast(0))
            params.y = top.coerceAtLeast(0)
            routeBtn?.layoutParams = LinearLayout.LayoutParams(btnW, btnH)
        } else {
            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            params.x = dp(72)
            params.y = computeFallbackY(panelTarget.panelBottomPx)
            routeBtn?.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            routeBtn?.minimumHeight = dp(42)
        }
    }

    private fun computeFallbackY(panelBottomPx: Int?): Int {
        val screenH = context.resources.displayMetrics.heightPixels
        val fromPanel = panelBottomPx?.let { screenH - it + dp(8) } ?: dp(120)
        return fromPanel.coerceAtLeast(dp(80))
    }

    private fun styleRouteButton(panelTarget: MapRelocPanelTarget) {
        val btn = routeBtn ?: return
        val density = context.resources.displayMetrics.density
        val heightPx = panelTarget.relocBtnHeightPx ?: dp(42)
        val textSp = (heightPx / density * 0.34f).coerceIn(11f, 16f)
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)

        val baseRgb = panelTarget.relocBtnColorRgb ?: DEFAULT_GAME_BTN_RGB
        val topColor = brighten(baseRgb, 1.08f)
        val bottomColor = darken(baseRgb, 0.88f)
        val corner = (heightPx * 0.22f).coerceIn(dp(8).toFloat(), dp(14).toFloat())
        btn.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topColor or 0xFF000000.toInt(), bottomColor or 0xFF000000.toInt()),
        ).apply {
            this.cornerRadius = corner
            setStroke(dp(1), Color.argb(70, 255, 255, 255))
        }
        val hPad = (heightPx * 0.28f).toInt().coerceAtLeast(dp(10))
        btn.setPadding(hPad, 0, hPad, 0)
        btn.gravity = Gravity.CENTER
    }

    private fun buildView(): LinearLayout {
        val routeButton = TextView(context).apply {
            text = context.getString(R.string.overlay_route_map_button)
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setOnClickListener {
                val t = target ?: return@setOnClickListener
                onRouteClick(t)
            }
        }
        routeBtn = routeButton
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                routeButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun brighten(rgb: Int, factor: Float): Int {
        fun ch(c: Int) = (c * factor).toInt().coerceIn(0, 255)
        return (ch((rgb shr 16) and 0xFF) shl 16) or (ch((rgb shr 8) and 0xFF) shl 8) or ch(rgb and 0xFF)
    }

    private fun darken(rgb: Int, factor: Float): Int {
        fun ch(c: Int) = (c * factor).toInt().coerceIn(0, 255)
        return (ch((rgb shr 16) and 0xFF) shl 16) or (ch((rgb shr 8) and 0xFF) shl 8) or ch(rgb and 0xFF)
    }

    companion object {
        private const val TAG = "OverlayMapRelocStrip"
        /** Типичный зелёный «Перемещение» в игре (fallback). */
        private const val DEFAULT_GAME_BTN_RGB = 0x3D8A5C
    }
}
