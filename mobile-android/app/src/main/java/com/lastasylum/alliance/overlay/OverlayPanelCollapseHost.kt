package com.lastasylum.alliance.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.lastasylum.alliance.R

/**
 * Кнопка «Свернуть/развернуть» с иконкой замка в углу (без отдельного фона — только tint).
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
        styleLockIcon(lockButton)

        val hostSide = dp(44)
        val lockSide = dp(16)

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
                    topMargin = -dp(2)
                    marginEnd = dp(0)
                },
            )
            lockButton.translationZ = 3f
            collapseButton.translationZ = 1f
        }
    }

    fun applyLockVisual(lockButton: ImageView, locked: Boolean) {
        lockButton.setImageResource(
            if (locked) R.drawable.ic_overlay_lock_locked else R.drawable.ic_overlay_lock_open,
        )
        lockButton.imageTintList = ColorStateList.valueOf(
            Color.parseColor(if (locked) "#FFFFE082" else "#FFB0BEC5"),
        )
        lockButton.alpha = if (locked) 1f else 0.82f
    }

    private fun styleLockIcon(lockButton: ImageView) {
        lockButton.scaleType = ImageView.ScaleType.CENTER
        lockButton.background = null
        lockButton.setPadding(0, 0, 0, 0)
        lockButton.imageTintList = ColorStateList.valueOf(Color.parseColor("#FFB0BEC5"))
    }
}
