package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayGameGatePollPolicyTest {
    @Test
    fun mainAppForeground_usesWarmPollWhenRecentlyInGame() {
        val now = 100_000L
        val delay = OverlayGameGatePollPolicy.nextPollDelayMs(
            mainAppForegroundActive = true,
            inGameOverlayUiActive = false,
            overlayShellActive = false,
            modalUiActive = false,
            lastOverlayInGameAtMs = now - 10_000L,
            stableGatePollTicks = 0,
            stableTicksForSlowPoll = 5,
            nowMs = now,
        )
        assertEquals(OverlayGameGatePollPolicy.Poll.WARM_MS, delay)
    }

    @Test
    fun stableInGameOverlay_usesSlowPoll() {
        val delay = OverlayGameGatePollPolicy.nextPollDelayMs(
            mainAppForegroundActive = false,
            inGameOverlayUiActive = true,
            overlayShellActive = true,
            modalUiActive = false,
            lastOverlayInGameAtMs = 0L,
            stableGatePollTicks = 5,
            stableTicksForSlowPoll = 5,
        )
        assertEquals(OverlayGameGatePollPolicy.Poll.STABLE_MS, delay)
    }

    @Test
    fun entryBoost_usesFastPollWhenHudNotAttached() {
        val delay = OverlayGameGatePollPolicy.nextPollDelayMs(
            mainAppForegroundActive = false,
            inGameOverlayUiActive = false,
            overlayShellActive = false,
            modalUiActive = false,
            lastOverlayInGameAtMs = 0L,
            stableGatePollTicks = 0,
            stableTicksForSlowPoll = 5,
            entryBoostActive = true,
            overlayPanelEnabled = true,
        )
        assertEquals(OverlayGameGatePollPolicy.Poll.ENTRY_FAST_MS, delay)
    }
}
