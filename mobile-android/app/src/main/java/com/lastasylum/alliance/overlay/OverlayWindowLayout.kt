package com.lastasylum.alliance.overlay

import android.os.Build
import android.view.WindowManager

/**
 * Общие [WindowManager.LayoutParams] для окон оверлея: флаги и вырезы.
 *
 * **Конфликты с игрой (аудит поверхностей):**
 * - **Панель кнопок** ([CombatOverlayService]): `WRAP_CONTENT` окно, без `MATCH_PARENT` по ширине у контента;
 *   [FLAG_NOT_TOUCH_MODAL] — касания вне прямоугольника окна уходят в игру;
 *   корень [OverlayPassthroughMultitouchFrameLayout] — при pinch (>1 пальца) окно не участвует в dispatch (зум карты).
 * - **Лента чата**: отдельное окно; ширина по контенту + центр, не на весь экран; [OverlayStripScrollView] не
 *   перехватывает вертикаль, если прокрутка не нужна.
 * - **Тикер**: узкая полоса сверху, те же флаги `popupWindowFlags`, корень [OverlayPassthroughMultitouchFrameLayout].
 * - **Quick commands**: маленькие `WRAP_CONTENT` окна у пузыря, тот же корень для pinch.
 * - **Полноэкранный чат**: свои флаги [historyPanelWindowFlags] (фокус + IME), без NOT_TOUCH_MODAL — окно
 *   должно полностью перехватыть ввод, пока открыт чат.
 */
object OverlayWindowLayout {
    /**
     * Флаги для TYPE_APPLICATION_OVERLAY: полноэкранные игры и вырезы.
     * [FLAG_NOT_TOUCH_MODAL] — касания вне прямоугольника оверлей-окна уходят в приложение под ним;
     * без него при «раздутой» по ширине панели игра теряет свайпы/тапы по пустым зонам внизу экрана.
     */
    fun popupWindowFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    /** Полноэкранная панель истории: без NOT_FOCUSABLE — нужны поле ввода и IME. */
    fun historyPanelWindowFlags(): Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
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
