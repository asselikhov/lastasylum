package com.lastasylum.alliance.overlay

import android.content.Context
import android.util.DisplayMetrics
import android.widget.LinearLayout
import android.widget.TextView
import com.airbnb.lottie.LottieAnimationView

/**
 * Правила вёрстки входящей реакции (аудит: игра / размер / обрезка / фон / FPS).
 *
 * **Игра:** [OverlayWindowLayout.applyReactionBurstWindowTouchPolicy] (alpha **окна** ≤ 0.8 на Android 12+)
 * + [OverlayReactionBurstTouchRoot] (без input channel на API 33+).
 *
 * **Прозрачность:** задаётся alpha окна ([OverlayWindowLayout.reactionBurstWindowAlpha]), не только View.alpha.
 *
 * **Размер:** доля экрана по ширине и высоте, чтобы не перекрывать HUD и не вылезать за safe area.
 *
 * **Обрезка:** только WRAP_CONTENT + maxWidth/maxHeight, clipChildren=false, padding = запас под scale.
 *
 * **Фон:** без Drawable у текстов и карточек; только тени.
 *
 * **Анимация:** Lottie в цикле + keep-alive (как в меню реакций).
 */
internal object OverlayReactionBurstLayout {
    /** Контент рисуется непрозрачным; лёгкая прозрачность — через LayoutParams.alpha окна. */
    const val CONTENT_ALPHA = 1f

    const val CAPTION_ALPHA = 1f
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

    /**
     * Lottie рисуется в квадрате [maxSide×maxSide] с letterbox; мемы/стикеры — по [adjustViewBounds].
     * Подтягиваем подпись к видимому низу анимации, не меняя размер Lottie.
     */
    fun applyCaptionMarginTightBelowLottie(
        lottie: LottieAnimationView,
        senderLine: TextView,
        baseMarginPx: Int,
    ) {
        val comp = lottie.composition ?: return
        val bounds = comp.bounds
        val boundsW = bounds.width()
        val boundsH = bounds.height()
        if (boundsW <= 0f || boundsH <= 0f) return
        val viewW = lottie.width
        val viewH = lottie.height
        if (viewW <= 0 || viewH <= 0) return
        val scale = minOf(viewW / boundsW, viewH / boundsH)
        val drawnH = boundsH * scale
        val letterboxBottomPx = ((viewH - drawnH) / 2f).toInt().coerceAtLeast(0)
        val lp = senderLine.layoutParams as? LinearLayout.LayoutParams ?: return
        lp.topMargin = (baseMarginPx - letterboxBottomPx).coerceAtLeast(0)
        senderLine.layoutParams = lp
    }

    fun scheduleCaptionMarginTightBelowLottie(
        lottie: LottieAnimationView,
        senderLine: TextView,
        baseMarginPx: Int,
    ) {
        val apply = {
            lottie.post {
                applyCaptionMarginTightBelowLottie(lottie, senderLine, baseMarginPx)
            }
        }
        lottie.addLottieOnCompositionLoadedListener { apply() }
        apply()
    }
}
