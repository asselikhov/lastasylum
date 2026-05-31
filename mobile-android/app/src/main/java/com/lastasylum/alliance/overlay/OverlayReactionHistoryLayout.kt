package com.lastasylum.alliance.overlay

internal enum class HistoryLayoutMode {
    FAN,
    RIVER,
}

internal data class HistoryTileOffset(
    val translationX: Float,
    val translationY: Float,
)

/** Fan arc + river scroll layout for history mini tiles. */
internal object OverlayReactionHistoryLayout {
    const val FAN_ARC_Y_DP = 8
    const val FAN_OVERLAP_X_DP = 12
    const val RIVER_GAP_DP = 4

    fun modeFor(burstMode: Boolean, historyCount: Int): HistoryLayoutMode =
        if (burstMode && historyCount >= OverlayReactionStageLayout.MAX_HISTORY_TILES) {
            HistoryLayoutMode.RIVER
        } else {
            HistoryLayoutMode.FAN
        }

    fun fanOffsets(
        index: Int,
        count: Int,
        miniPx: Int,
        gapPx: Int,
        overlapPx: Int,
        arcYPx: Int,
    ): HistoryTileOffset {
        if (count <= 0) return HistoryTileOffset(0f, 0f)
        val center = (count - 1) / 2f
        val delta = index - center
        val stepX = (miniPx + gapPx - overlapPx).toFloat()
        val translationX = delta * stepX
        val arcFactor = if (count <= 1) 0f else delta / center.coerceAtLeast(1f)
        val translationY = kotlin.math.abs(arcFactor) * arcYPx
        return HistoryTileOffset(translationX, translationY)
    }

    fun riverScrollTargetX(contentWidthPx: Int, viewportWidthPx: Int): Int =
        (contentWidthPx - viewportWidthPx).coerceAtLeast(0)
}
