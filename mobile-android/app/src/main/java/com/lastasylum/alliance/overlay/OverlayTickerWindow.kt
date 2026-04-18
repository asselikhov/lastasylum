package com.lastasylum.alliance.overlay

import android.content.Context
import android.os.Build
import android.os.Handler
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * Top toast-style ticker window (separate overlay layer from main combat strip).
 */
class OverlayTickerWindow(
    private val context: Context,
    private val windowManagerProvider: () -> WindowManager?,
    private val mainHandler: Handler,
    private val dp: (Int) -> Int,
    private val tickerYDp: () -> Int,
) {
    private var tickerView: TextView? = null
    private var tickerParams: WindowManager.LayoutParams? = null

    private val tickerHideRunnable = Runnable { removeTickerFromWindowSync() }

    fun ensureTicker() {
        if (tickerView != null) return
        val manager = windowManagerProvider() ?: return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val screenWidth = context.resources.displayMetrics.widthPixels
        val horizontalMargin = dp(HORIZONTAL_MARGIN_DP)
        val params = WindowManager.LayoutParams(
            (screenWidth - horizontalMargin * 2).coerceAtLeast(dp(180)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            OverlayWindowLayout.popupWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            gravity = Gravity.TOP or Gravity.START
            x = horizontalMargin
            y = dp(tickerYDp())
        }

        val ticker = TextView(context).apply {
            text = ""
            alpha = 0f
            OverlayTickerUi.applyTickerStyle(context, this)
        }

        tickerView = ticker
        tickerParams = params
        manager.addView(ticker, params)
    }

    fun showTicker(message: String) {
        mainHandler.post {
            ensureTicker()
            val ticker = tickerView ?: return@post
            ticker.text = message
            ticker.animate().alpha(1f).setDuration(SHOW_ALPHA_MS).start()
            mainHandler.removeCallbacks(tickerHideRunnable)
            mainHandler.postDelayed(tickerHideRunnable, HIDE_DELAY_MS)
        }
    }

    fun hideTicker() {
        mainHandler.post { removeTickerFromWindowSync() }
    }

    fun syncTickerPosition() {
        val manager = windowManagerProvider() ?: return
        val params = tickerParams ?: return
        val ticker = tickerView ?: return
        params.x = dp(HORIZONTAL_MARGIN_DP)
        params.y = dp(tickerYDp())
        runCatching { manager.updateViewLayout(ticker, params) }
    }

    private fun removeTickerFromWindowSync() {
        val manager = windowManagerProvider() ?: return
        val ticker = tickerView ?: return
        runCatching { manager.removeView(ticker) }
        tickerView = null
        tickerParams = null
        mainHandler.removeCallbacks(tickerHideRunnable)
    }

    private companion object {
        const val HORIZONTAL_MARGIN_DP = 10
        private const val HIDE_DELAY_MS = 5000L
        private const val SHOW_ALPHA_MS = 160L
    }
}
