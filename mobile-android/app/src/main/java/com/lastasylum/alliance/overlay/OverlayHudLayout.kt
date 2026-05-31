package com.lastasylum.alliance.overlay

import android.view.Gravity
import android.view.WindowManager

/**
 * Fixed HUD window placement — do not change without explicit product approval.
 * @see OVERLAY_HUD_WINDOW_X_DP
 * @see OVERLAY_HUD_WINDOW_Y_DP
 */
internal object OverlayHudLayout {
    const val WINDOW_X_DP = 10
    const val WINDOW_Y_DP = 2
    const val CHAT_STRIP_BELOW_HUD_GAP_DP = 8
    const val HUD_CHIP_ESTIMATE_HEIGHT_DP = 28

    fun chatStripTopOffsetDp(): Int =
        WINDOW_Y_DP + HUD_CHIP_ESTIMATE_HEIGHT_DP + CHAT_STRIP_BELOW_HUD_GAP_DP

    fun applyStatusHudPosition(params: WindowManager.LayoutParams, dp: (Int) -> Int) {
        params.gravity = Gravity.TOP or Gravity.START
        params.x = dp(WINDOW_X_DP)
        params.y = dp(WINDOW_Y_DP)
    }

    fun applyTopRightHudPosition(params: WindowManager.LayoutParams, dp: (Int) -> Int) {
        params.gravity = Gravity.TOP or Gravity.END
        params.x = dp(WINDOW_X_DP)
        params.y = dp(WINDOW_Y_DP)
    }
}
