package com.lastasylum.alliance.overlay

import android.content.Context
import android.util.DisplayMetrics

/**
 * Правила вёрстки входящей реакции (аудит: игра / размер / обрезка / фон / FPS).
 *
 * **Игра:** окно только [OverlayWindowLayout.reactionBurstWindowFlags] (NOT_TOUCHABLE, без NOT_TOUCH_MODAL),
 * корень [OverlayPassthroughMultitouchFrameLayout] — жесты уходят в игру.
 *
 * **Размер:** доля экрана по ширине и высоте, чтобы не перекрывать HUD и не вылезать за safe area.
 *
 * **Обрезка:** только WRAP_CONTENT + maxWidth/maxHeight, clipChildren=false, padding = запас под scale.
 *
 * **Фон:** без Drawable у текстов и карточек; только тени.
 *
 * **Анимация:** Lottie в цикле + keep-alive (как в меню реакций), без fade alpha на контенте.
 */
internal object OverlayReactionBurstLayout {
    private const val WIDTH_FRACTION = 0.56f
    private const val HEIGHT_FRACTION = 0.28f
    private const val MAX_SIDE_DP = 280
    private const val MIN_SIDE_DP = 112
    private const val TEXT_WIDTH_FRACTION = 0.86f
    private const val MIN_TEXT_WIDTH_DP = 148

    /** Запас вокруг анимации под enter scale (~1.06), без раздувания hit-area. */
    private const val SCALE_PAD_DP = 14

    /** Окно вспышки ниже верхней ленты чата, чтобы подпись не уезжала за край экрана. */
    const val WINDOW_TOP_Y_DP = 72

    /** Отступ подписи отправителя под картинкой мема / Lottie / стикера. */
    const val SENDER_BELOW_ANIM_DP = 8

    data class Metrics(
        val animSidePx: Int,
        val animPadPx: Int,
        val maxTextWidthPx: Int,
        val screenWidthPx: Int,
        val screenHeightPx: Int,
    )

    fun metrics(context: Context, dp: (Int) -> Int): Metrics {
        val dm: DisplayMetrics = context.resources.displayMetrics
        val maxW = (dm.widthPixels * WIDTH_FRACTION).toInt()
        val maxH = (dm.heightPixels * HEIGHT_FRACTION).toInt()
        val cap = dp(MAX_SIDE_DP)
        val floor = dp(MIN_SIDE_DP)
        val animSide = minOf(maxW, maxH, cap).coerceAtLeast(floor)
        val textW = (dm.widthPixels * TEXT_WIDTH_FRACTION).toInt().coerceAtLeast(dp(MIN_TEXT_WIDTH_DP))
        return Metrics(
            animSidePx = animSide,
            animPadPx = dp(SCALE_PAD_DP),
            maxTextWidthPx = textW,
            screenWidthPx = dm.widthPixels,
            screenHeightPx = dm.heightPixels,
        )
    }

    fun animSideForReaction(reaction: OverlayQuickReaction, metrics: Metrics, dp: (Int) -> Int): Int {
        if (reaction.lottieRawRes != null) return metrics.animSidePx
        if (
            reaction.gifDrawableRes != null ||
            reaction.memeDrawableRes != null ||
            reaction.stickerAssetStem != null
        ) {
            return metrics.animSidePx
        }
        return minOf(metrics.animSidePx, dp(136))
    }
}
