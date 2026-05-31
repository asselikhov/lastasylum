package com.lastasylum.alliance.overlay

/** Hero + history strip layout under top-right HUD. */
internal object OverlayReactionStageLayout {
    const val HERO_TILE_DP = 96
    const val MINI_TILE_DP = 40
    const val MAX_HISTORY_TILES = 4
    const val MAX_PLAYING_LOTTIES = 2
    const val HISTORY_GAP_DP = 6
    const val HERO_CAPTION_MAX_WIDTH_DP = 120
    const val HERO_CAPTION_TOP_MARGIN_DP = 4
    const val STAGE_MAX_HEIGHT_FRACTION = 0.35f
    const val HERO_VISIBLE_MS = 4_500L
    const val HERO_EXTEND_MS = 1_500L
    const val MINI_VISIBLE_MS = 3_000L
    const val BURST_MINI_VISIBLE_MS = 2_000L
    const val BURST_WINDOW_MS = 1_000L
    const val BURST_MIN_EVENTS = 3
    const val REFLOW_MS = 240L
    const val ENTER_FROM_ANCHOR_Y_DP = 12
    const val EXIT_SLIDE_Y_DP = 12

    fun heroTileSizePx(dp: (Int) -> Int): Int = dp(HERO_TILE_DP)

    fun miniTileSizePx(dp: (Int) -> Int): Int = dp(MINI_TILE_DP)

    fun maxStageHeightPx(screenHeightPx: Int): Int =
        (screenHeightPx * STAGE_MAX_HEIGHT_FRACTION).toInt()

    fun shouldEvictOldestHistory(historyCount: Int): Boolean =
        historyCount > MAX_HISTORY_TILES

    fun heroExpiryMs(extended: Boolean): Long =
        if (extended) HERO_VISIBLE_MS + HERO_EXTEND_MS else HERO_VISIBLE_MS

    fun miniExpiryMs(burstMode: Boolean): Long =
        if (burstMode) BURST_MINI_VISIBLE_MS else MINI_VISIBLE_MS
}

internal enum class OverlayReactionTileMode {
    HERO,
    MINI,
}
