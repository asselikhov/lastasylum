package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayMainAppPresencePolicyTest {
    @Test
    fun skipAway_whenOverlayUiActive() {
        assertTrue(
            OverlayMainAppPresencePolicy.shouldSkipAwayPing(
                inGameOverlayUiActive = true,
                targetGameForeground = false,
            ),
        )
    }

    @Test
    fun skipAway_whenTargetGameForeground() {
        assertTrue(
            OverlayMainAppPresencePolicy.shouldSkipAwayPing(
                inGameOverlayUiActive = false,
                targetGameForeground = true,
            ),
        )
    }

    @Test
    fun sendAway_whenNeitherOverlayNorGameForeground() {
        assertFalse(
            OverlayMainAppPresencePolicy.shouldSkipAwayPing(
                inGameOverlayUiActive = false,
                targetGameForeground = false,
            ),
        )
    }

    @Test
    fun skipOnline_whenTargetGameForeground() {
        assertTrue(
            OverlayMainAppPresencePolicy.shouldSkipOnlinePing(
                inGameOverlayUiActive = false,
                targetGameForeground = true,
            ),
        )
    }
}
