package com.lastasylum.alliance.overlay

/** Hero + history strip layout under top-right HUD. */
internal object OverlayReactionStageLayout {
    const val HERO_WIDTH_FRACTION = 0.40f
    const val HERO_MAX_DP = 168
    const val HERO_MIN_DP = 128
    const val MINI_HERO_RATIO = 0.42f
    const val MINI_MIN_DP = 52
    const val MAX_HISTORY_TILES = 4
    const val MAX_PLAYING_LOTTIES = 2
    const val HISTORY_GAP_DP = 6
    const val HERO_CAPTION_TOP_MARGIN_DP = 6
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
    const val TEXT_HERO_SP = 24f
    const val TEXT_HERO_MAX_LINES = 4
    const val TEXT_MINI_SP = 11f
    const val STAGGER_MS = 80L
    const val DEMOTE_ANIM_MS = 280L
    const val MINI_SCALE_RATIO = MINI_HERO_RATIO

    fun heroTileSizePx(screenWidthPx: Int, dp: (Int) -> Int): Int =
        minOf((screenWidthPx * HERO_WIDTH_FRACTION).toInt(), dp(HERO_MAX_DP))
            .coerceAtLeast(dp(HERO_MIN_DP))

    fun miniTileSizePx(screenWidthPx: Int, dp: (Int) -> Int): Int {
        val hero = heroTileSizePx(screenWidthPx, dp)
        return (hero * MINI_HERO_RATIO).toInt().coerceAtLeast(dp(MINI_MIN_DP))
    }

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
