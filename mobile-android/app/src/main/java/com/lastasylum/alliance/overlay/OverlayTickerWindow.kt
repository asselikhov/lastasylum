package com.lastasylum.alliance.overlay

import android.content.Context
import android.os.Build
import android.os.Handler
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
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
    /** Вызывается после [addView] тикера — чтобы поднять полноэкранный чат поверх вспомогательных overlay-окон. */
    private val onTickerWindowAttached: () -> Unit = {},
) {
    /** Корень окна (вешается на [WindowManager]); текст — дочерний [TextView]. */
    private var tickerHost: OverlayPassthroughMultitouchFrameLayout? = null
    private var tickerView: TextView? = null
    private var tickerParams: WindowManager.LayoutParams? = null
    /** Сохранённые флаги до [applyTouchPassthrough], чтобы восстановить после системного пикера. */
    private var tickerFlagsBeforePassthrough: Int? = null

    private val tickerHideRunnable = Runnable { removeTickerFromWindowSync() }

    /** Окно тикера создаётся только при первом показе — иначе широкая полоска с alpha=0 перехватывала тапы по игре сверху. */
    private fun attachTickerWindowIfNeeded() {
        if (tickerHost != null) return
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

        val host = OverlayPassthroughMultitouchFrameLayout(context).apply {
            addView(
                ticker,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        tickerHost = host
        tickerView = ticker
        tickerParams = params
        manager.addView(host, params)
        onTickerWindowAttached()
    }

    fun showTicker(message: String) {
        mainHandler.post {
            attachTickerWindowIfNeeded()
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
        val host = tickerHost ?: return
        params.x = dp(HORIZONTAL_MARGIN_DP)
        params.y = dp(tickerYDp())
        runCatching { manager.updateViewLayout(host, params) }
    }

    /**
     * Пока открыта Activity с системным пикером/разрешениями, overlay-окна остаются выше неё по Z-order.
     * [FLAG_NOT_TOUCHABLE] на тикере (если окно есть) пропускает тапы к Activity под оверлеем.
     */
    fun applyTouchPassthrough(enable: Boolean) {
        mainHandler.post {
            val manager = windowManagerProvider() ?: return@post
            val params = tickerParams ?: return@post
            val host = tickerHost ?: return@post
            if (!host.isAttachedToWindow) return@post
            if (enable) {
                if (tickerFlagsBeforePassthrough == null) {
                    tickerFlagsBeforePassthrough = params.flags
                }
                val with = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                if (params.flags != with) {
                    params.flags = with
                    runCatching { manager.updateViewLayout(host, params) }
                }
            } else {
                val saved = tickerFlagsBeforePassthrough ?: return@post
                tickerFlagsBeforePassthrough = null
                params.flags = saved
                runCatching { manager.updateViewLayout(host, params) }
            }
        }
    }

    private fun removeTickerFromWindowSync() {
        val manager = windowManagerProvider() ?: return
        val host = tickerHost ?: return
        runCatching { manager.removeView(host) }
        tickerHost = null
        tickerView = null
        tickerParams = null
        tickerFlagsBeforePassthrough = null
        mainHandler.removeCallbacks(tickerHideRunnable)
    }

    private companion object {
        const val HORIZONTAL_MARGIN_DP = 10
        private const val HIDE_DELAY_MS = 5000L
        private const val SHOW_ALPHA_MS = 160L
    }
}
