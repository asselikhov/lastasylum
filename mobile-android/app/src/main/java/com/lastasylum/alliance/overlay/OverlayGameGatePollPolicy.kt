package com.lastasylum.alliance.overlay

/**
 * Adaptive game-gate poll intervals for [CombatOverlayService].
 */
internal object OverlayGameGatePollPolicy {
    const val RECENT_INGAME_WINDOW_MS = 45_000L

    fun nextPollDelayMs(
        mainAppForegroundActive: Boolean,
        inGameOverlayUiActive: Boolean,
        overlayShellActive: Boolean,
        modalUiActive: Boolean,
        lastOverlayInGameAtMs: Long,
        stableGatePollTicks: Int,
        stableTicksForSlowPoll: Int,
        entryBoostActive: Boolean = false,
        overlayPanelEnabled: Boolean = true,
        nowMs: Long = System.currentTimeMillis(),
    ): Long {
        if (entryBoostActive && overlayPanelEnabled && !overlayShellActive) {
            return Poll.ENTRY_FAST_MS
        }
        if (mainAppForegroundActive && !inGameOverlayUiActive && !overlayShellActive) {
            val last = lastOverlayInGameAtMs
            return if (last > 0L && nowMs - last <= RECENT_INGAME_WINDOW_MS) {
                Poll.WARM_MS
            } else {
                Poll.IDLE_MS
            }
        }
        if (modalUiActive) {
            return Poll.MODAL_UI_MS
        }
        if (!inGameOverlayUiActive) {
            val last = lastOverlayInGameAtMs
            return if (last > 0L && nowMs - last <= RECENT_INGAME_WINDOW_MS) {
                Poll.WARM_MS
            } else {
                Poll.IDLE_MS
            }
        }
        if (overlayShellActive && stableGatePollTicks >= stableTicksForSlowPoll) {
            return Poll.STABLE_MS
        }
        if (overlayShellActive) return Poll.ACTIVE_MS
        return Poll.IDLE_MS
    }

    object Poll {
        const val ACTIVE_MS = 1_200L
        const val ENTRY_FAST_MS = 800L
        const val STABLE_MS = 3_500L
        const val MODAL_UI_MS = 5_000L
        const val WARM_MS = 1_800L
        const val IDLE_MS = 6_000L
    }
}
