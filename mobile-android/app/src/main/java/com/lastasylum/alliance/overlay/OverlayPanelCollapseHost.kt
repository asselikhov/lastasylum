package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.TouchDelegate
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Кнопка «Свернуть/развернуть» с чипом замка в правом верхнем углу (как badge на FAB).
 * Экономит место в столбце панели; замок доступен и в свёрнутом режиме.
 */
internal object OverlayPanelCollapseHost {

    fun build(
        context: Context,
        fabCtx: Context,
        dp: (Int) -> Int,
        collapseButton: ImageView,
        lockButton: ImageView,
    ): FrameLayout {
        OverlayTickerUi.styleOverlayIconButton(fabCtx, collapseButton, sideDp = 42f)
        OverlayTickerUi.styleOverlayLockChipBase(fabCtx, lockButton)

        val hostSide = dp(44)
        val lockSide = dp(26)
        val cornerOverlap = dp(3)

        return FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            layoutParams = ViewGroup.LayoutParams(hostSide, hostSide)

            addView(
                collapseButton,
                FrameLayout.LayoutParams(hostSide, hostSide),
            )
            addView(
                lockButton,
                FrameLayout.LayoutParams(lockSide, lockSide, Gravity.TOP or Gravity.END).apply {
                    topMargin = -cornerOverlap
                    marginEnd = -cornerOverlap
                },
            )
            lockButton.translationZ = 6f
            collapseButton.translationZ = 2f

            post { installLockTouchDelegate(this, lockButton, dp(10)) }
        }
    }

    private fun installLockTouchDelegate(host: ViewGroup, lockButton: ImageView, expandPx: Int) {
        val rect = Rect()
        lockButton.getHitRect(rect)
        if (rect.isEmpty) {
            host.post { installLockTouchDelegate(host, lockButton, expandPx) }
            return
        }
        rect.inset(-expandPx, -expandPx)
        host.touchDelegate = TouchDelegate(rect, lockButton)
    }
}
