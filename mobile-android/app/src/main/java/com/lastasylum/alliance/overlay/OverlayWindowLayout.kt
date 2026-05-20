package com.lastasylum.alliance.overlay

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Общие [WindowManager.LayoutParams] для окон оверлея: флаги и вырезы.
 *
 * **Конфликты с игрой (аудит поверхностей):**
 * - **Панель кнопок** ([CombatOverlayService]): `WRAP_CONTENT` окно, без `MATCH_PARENT` по ширине у контента;
 *   [FLAG_NOT_TOUCH_MODAL] — касания вне прямоугольника окна уходят в игру;
 *   корень [OverlayPassthroughMultitouchFrameLayout] — при pinch (>1 пальца) окно не участвует в dispatch (зум карты).
 * - **Лента чата**: отдельное окно; ширина по контенту + центр; вертикальный скролл и max-height от экрана;
 *   касания — [OverlayStripPassthroughFrameLayout]: проходят в игру, кроме зон крестика закрытия.
 * - **Тикер**: окно создаётся только при первом [OverlayTickerWindow.showTicker], чтобы не держать
 *   невидимую полоску на всю ширину экрана между сообщениями.
 * - **Quick commands**: маленькие `WRAP_CONTENT` окна у пузыря, тот же корень для pinch.
 * - **Полноэкранный чат**: свои флаги [historyPanelWindowFlags] (фокус + IME), без NOT_TOUCH_MODAL — окно
 *   должно полностью перехватыть ввод, пока открыт чат.
 */
object OverlayWindowLayout {
    /**
     * Флаги для TYPE_APPLICATION_OVERLAY: полноэкранные игры и вырезы.
     * - [FLAG_NOT_TOUCH_MODAL] — касания вне прямоугольника окна уходят в приложение под ним.
     * - [FLAG_SPLIT_TOUCH] — корректнее раздавать ввод между оверлеем и игрой при нескольких окнах.
     * Без [FLAG_LAYOUT_NO_LIMITS]: на части прошивок он раздувает область hit-теста за пределы видимого UI.
     */
    fun popupWindowFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_SPLIT_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    /**
     * Вспышка входящей реакции: только анимация, касания проходят в игру.
     */
    fun reactionBurstWindowFlags(): Int =
        popupWindowFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    /** Полноэкранная панель истории: без NOT_FOCUSABLE — нужны поле ввода и IME. */
    fun historyPanelWindowFlags(): Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    fun applyPopupLayoutCompat(params: WindowManager.LayoutParams) {
        applyDisplayCutoutCompat(params)
        applyZeroFitInsets(params)
    }

    fun applyHistoryLayoutCompat(params: WindowManager.LayoutParams) {
        applyDisplayCutoutCompat(params)
    }

    /**
     * Полноэкранные overlay-окна (чат, скрим команд): на MIUI/HyperOS высота окна иногда
     * меньше физического экрана — задаём реальную высоту дисплея и отключаем fitInsets.
     */
    fun applyFullscreenOverlayWindow(context: Context, params: WindowManager.LayoutParams) {
        applyHistoryLayoutCompat(params)
        applyZeroFitInsets(params)
        params.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        params.x = 0
        params.y = 0
        val h = realDisplayHeightPx(context)
        if (h > 0) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = h
        }
    }

    /**
     * Полноэкранный оверлей-чат: без системного resize (на TYPE_APPLICATION_OVERLAY он часто не
     * сдвигает контент как у Activity). Подъём контента — через [android.view.View] padding от
     * [android.view.WindowInsetsCompat.Type.ime] на корневом [android.widget.FrameLayout].
     */
    fun applyOverlayFullscreenChatSoftInputMode(params: WindowManager.LayoutParams) {
        @Suppress("DEPRECATION")
        val mode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        params.softInputMode = mode
    }

    /**
     * Прочие overlay-диалоги, где нужен системный resize под IME (не полноэкранный Compose-чат).
     */
    fun applyHistoryPanelSoftInputMode(params: WindowManager.LayoutParams) {
        @Suppress("DEPRECATION")
        val mode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        params.softInputMode = mode
    }

    /** IME для диалога координат в отдельном overlay-окне. */
    fun applyCoordinateDialogSoftInputMode(params: WindowManager.LayoutParams) {
        @Suppress("DEPRECATION")
        val mode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        params.softInputMode = mode
    }

    private fun applyDisplayCutoutCompat(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun applyZeroFitInsets(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.setFitInsetsTypes(0)
            params.setFitInsetsSides(0)
        }
    }

    fun realDisplayHeightPx(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return wm.currentWindowMetrics.bounds.height().coerceAtLeast(0)
        }
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay?.getRealMetrics(dm)
        return dm.heightPixels.coerceAtLeast(0)
    }
}
