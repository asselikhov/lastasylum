package com.lastasylum.alliance.overlay

import android.os.Build
import android.view.WindowManager

/**
 * Общие [WindowManager.LayoutParams] для окон оверлея: флаги и вырезы.
 */
object OverlayWindowLayout {
    /**
     * Флаги для TYPE_APPLICATION_OVERLAY: полноэкранные игры и вырезы.
     * Без [WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL]: иначе игра часто «забирает» касания у FAB.
     */
    fun popupWindowFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    /** Полноэкранная панель истории: без NOT_FOCUSABLE — нужны поле ввода и IME. */
    fun historyPanelWindowFlags(): Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    fun applyPopupLayoutCompat(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.setFitInsetsTypes(0)
        }
    }

    fun applyHistoryLayoutCompat(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}
