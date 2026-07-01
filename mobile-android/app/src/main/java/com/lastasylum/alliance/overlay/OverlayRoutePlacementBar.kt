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
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.lastasylum.alliance.R

/** Нижняя панель «Отмена» / «Проложить» во время выбора области 3×3. */
class OverlayRoutePlacementBar(
    private val context: Context,
    private val mainHandler: Handler,
    private val dp: (Int) -> Int,
    private val onCancel: () -> Unit,
    private val onConfirm: () -> Unit,
) {
    private var root: LinearLayout? = null
    private var attached = false
    private var attachedWindowManager: WindowManager? = null

    val isShowing: Boolean get() = attached

    fun show(windowManager: WindowManager) {
        runOnMain {
            val view = root ?: buildView().also { root = it }
            if (attached) return@runOnMain
            val params = buildParams()
            runCatching { windowManager.addView(view, params) }
                .onSuccess {
                    attached = true
                    attachedWindowManager = windowManager
                }
                .onFailure { e -> Log.w(TAG, "addView failed", e) }
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
            y = dp(48)
        }
    }

    private fun buildView(): LinearLayout {
        fun actionButton(
            label: String,
            colors: IntArray,
            stroke: Int,
            onClick: () -> Unit,
        ) = TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            minimumHeight = dp(44)
            minimumWidth = dp(120)
            setPadding(dp(22), dp(10), dp(22), dp(10))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), stroke)
            }
            setOnClickListener { onClick() }
        }

        val cancelBtn = actionButton(
            context.getString(R.string.overlay_route_planner_cancel),
            intArrayOf(Color.parseColor("#FF374151"), Color.parseColor("#FF1F2937")),
            Color.parseColor("#44FFFFFF"),
            onCancel,
        )
        val confirmBtn = actionButton(
            context.getString(R.string.overlay_route_placement_confirm),
            intArrayOf(Color.parseColor("#FF059669"), Color.parseColor("#FF047857")),
            Color.parseColor("#55FFFFFF"),
            onConfirm,
        )

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#E6141C2A"), Color.parseColor("#F50B1220")),
            ).apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#33445566"))
            }
            addView(cancelBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            })
            addView(confirmBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    companion object {
        private const val TAG = "OverlayRoutePlacementBar"
    }
}
