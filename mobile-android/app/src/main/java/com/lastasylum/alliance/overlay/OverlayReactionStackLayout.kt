package com.lastasylum.alliance.overlay

/**
 * Pure layout rules for the incoming-reaction stack (newest on top, largest).
 */
internal object OverlayReactionStackLayout {
    const val MAX_VISIBLE_SLOTS = 5
    const val MAX_PLAYING_LOTTIES = 2
    const val HEAD_VISIBLE_MS = 4_500L
    const val HEAD_EXTEND_MS = 1_500L
    const val TAIL_VISIBLE_MS = 3_000L
    const val BURST_TAIL_VISIBLE_MS = 2_000L
    const val BURST_WINDOW_MS = 1_000L
    const val BURST_MIN_EVENTS = 3
    const val SLOT_GAP_DP = 6
    const val STACK_REFLOW_MS = 240L
    const val SLOT_DEPTH_Y_DP = 6
    const val SLOT_DEPTH_X_DP = 3
    const val ENTER_FROM_ANCHOR_Y_DP = 12
    const val EXIT_SLIDE_Y_DP = 12

    private val SLOT_SCALES = floatArrayOf(1f, 0.88f, 0.76f, 0.66f, 0.58f)
    private val SLOT_ALPHAS = floatArrayOf(1f, 0.92f, 0.78f, 0.64f, 0.52f)
    private val BURST_SLOT_ALPHAS = floatArrayOf(1f, 0.88f, 0.68f, 0.52f, 0.40f)

    fun maxStageHeightPx(screenHeightPx: Int): Int =
        (screenHeightPx * OverlayReactionBurstLayout.MAX_STAGE_HEIGHT_FRACTION).toInt()

    fun slotScaleForIndex(index: Int): Float =
        SLOT_SCALES.getOrElse(index) { SLOT_SCALES.last() }

    fun slotAlphaForIndex(index: Int, burstMode: Boolean = false): Float {
        val ladder = if (burstMode) BURST_SLOT_ALPHAS else SLOT_ALPHAS
        return ladder.getOrElse(index) { ladder.last() }
    }

    fun slotTranslationYForIndex(index: Int, depthPx: Int): Float =
        index * depthPx.toFloat()

    fun slotTranslationXForIndex(index: Int, depthPx: Int, align: HorizontalAlign): Float =
        when (align) {
            HorizontalAlign.END -> -index * depthPx.toFloat()
            HorizontalAlign.CENTER -> 0f
        }

    fun visibleDurationMsForIndex(
        index: Int,
        totalSlots: Int,
        burstMode: Boolean = false,
    ): Long =
        when {
            index == 0 -> HEAD_VISIBLE_MS
            burstMode && totalSlots > 2 && index >= totalSlots - 1 -> BURST_TAIL_VISIBLE_MS
            totalSlots > 3 && index >= totalSlots - 1 -> TAIL_VISIBLE_MS
            else -> HEAD_VISIBLE_MS
        }

    fun headExpiryMs(extended: Boolean): Long =
        if (extended) HEAD_VISIBLE_MS + HEAD_EXTEND_MS else HEAD_VISIBLE_MS

    /** Drop oldest visible slot when over cap or estimated height exceeds budget. */
    fun shouldEvictOldestSlot(
        slotCount: Int,
        estimatedTotalHeightPx: Int,
        maxStageHeightPx: Int,
    ): Boolean =
        slotCount > MAX_VISIBLE_SLOTS ||
            (slotCount > 1 && estimatedTotalHeightPx > maxStageHeightPx)
}
