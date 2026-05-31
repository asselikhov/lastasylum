package com.lastasylum.alliance.overlay

import android.content.Context
import android.util.DisplayMetrics
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.TextView
import com.airbnb.lottie.LottieAnimationView

/**
 * Правила вёрстки входящей реакции (аудит: игра / размер / обрезка / фон / FPS).
 *
 * **Игра:** [OverlayWindowLayout.applyReactionBurstWindowTouchPolicy] (NOT_TOUCHABLE, alpha окна = 1)
 * + [OverlayReactionBurstTouchRoot] (без input channel на API 33+).
 *
 * **Прозрачность:** реакция (Lottie/GIF/стикер/текст) — alpha 1; полупрозрачный только фон подписи отправителя.
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
    /** Reaction visuals (Lottie/GIF/sticker/text) render fully opaque. */
    const val CONTENT_ALPHA = 1f

    /** Caption view alpha; semi-transparency lives only in [CAPTION_BACKGROUND_HEX]. */
    const val CAPTION_ALPHA = 1f

    /** Semi-transparent pill behind sender nick only (ARGB). */
    const val CAPTION_BACKGROUND_HEX = "#CC0D1524"
    private const val WIDTH_FRACTION = 0.56f
    private const val HEIGHT_FRACTION = 0.28f
    private const val MAX_SIDE_DP = 280
    private const val MIN_SIDE_DP = 112

    /** Fallback slot height when card not yet measured (stack eviction estimate). */
    const val MIN_SLOT_ESTIMATE_HEIGHT_DP = MIN_SIDE_DP + 40
    private const val TEXT_WIDTH_FRACTION = 0.86f
    private const val MIN_TEXT_WIDTH_DP = 148

    /** Запас вокруг анимации под enter scale (~1.06), без раздувания hit-area. */
    private const val SCALE_PAD_DP = 14

    /** Legacy fallback Y when no anchor (unused when anchor resolver is set). */
    const val WINDOW_TOP_Y_DP = 72

    /** Max height of the incoming-reaction stack on screen. */
    const val MAX_STAGE_HEIGHT_FRACTION = 0.5f

    /** HUD chip-aligned card (see OverlayGameHudChip). */
    const val CARD_FILL_HEX = "#B310141E"
    const val CARD_CORNER_DP = 6
    const val CARD_STROKE_BROADCAST_HEX = "#55FFB74D"
    const val CARD_STROKE_PRIVATE_HEX = "#4490CAF9"

    /** @deprecated use CARD_CORNER_DP */
    const val SLOT_CARD_CORNER_DP = CARD_CORNER_DP

    /** Отступ подписи отправителя под картинкой мема / Lottie / стикера. */
    const val SENDER_BELOW_ANIM_DP = 8

    /** Текстовая реакция: ширина с отступами от краёв экрана. */
    const val TEXT_HORIZONTAL_MARGIN_FRACTION = 0.08f

    const val TEXT_LINES_MAX = 5

    const val TEXT_MESSAGE_SP = 28f

    const val TEXT_SENDER_BELOW_MESSAGE_DP = 10

    data class Metrics(
        val animSidePx: Int,
        val animPadPx: Int,
        val maxTextWidthPx: Int,
        val screenWidthPx: Int,
        val screenHeightPx: Int,
    )

    fun textMessageMaxWidthPx(metrics: Metrics, minWidthPx: Int, anchorMaxWidthPx: Int? = null): Int {
        val margin = (metrics.screenWidthPx * TEXT_HORIZONTAL_MARGIN_FRACTION).toInt()
        val screenBased = (metrics.screenWidthPx - margin * 2).coerceAtLeast(minWidthPx)
        return anchorMaxWidthPx?.let { minOf(screenBased, it) } ?: screenBased
    }

    fun slotCardBackground(): GradientDrawable? = null

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
