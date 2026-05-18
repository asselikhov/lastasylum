package com.lastasylum.alliance.overlay

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Кнопка «Свернуть/развернуть» с компактным чипом замка в правом верхнем углу.
 * Чип мелкий и приподнят, чтобы не перехватывать тап по основной кнопке.
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
        OverlayTickerUi.styleOverlayLockChipBase(fabCtx, lockButton, sideDp = 18f)

        val hostSide = dp(44)
        val lockSide = dp(18)

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
                    topMargin = -dp(7)
                    marginEnd = dp(1)
                },
            )
            lockButton.translationZ = 4f
            collapseButton.translationZ = 2f
            // Без TouchDelegate — расширенная зона замка мешала нажатию «свернуть/развернуть».
        }
    }
}
