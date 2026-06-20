package com.lastasylum.alliance.overlay

/**
 * Game-entry boost: faster gate polling and lenient stable-show while HUD attaches after launch.
 */
internal object OverlayGameEntryPolicy {
    const val ENTRY_BOOST_MS = 15_000L
    const val NOT_IN_TARGET_DISMISS_STREAK = 2

    fun isEntryBoostActive(nowMs: Long, boostUntilMs: Long): Boolean =
        boostUntilMs > nowMs

    /**
     * During entry boost, keep HUD visible if any heuristic sees the target game.
     */
    fun shouldForceStableShowDuringEntry(
        entryBoostActive: Boolean,
        inGame: Boolean,
        fullHeuristicShow: Boolean,
        quickInTarget: Boolean,
    ): Boolean = entryBoostActive && (inGame || fullHeuristicShow || quickInTarget)
}
