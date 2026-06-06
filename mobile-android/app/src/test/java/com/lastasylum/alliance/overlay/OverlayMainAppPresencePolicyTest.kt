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
    fun skipAway_whenOverlayForegroundServiceActive() {
        assertTrue(
            OverlayMainAppPresencePolicy.shouldSkipAwayPing(
                inGameOverlayUiActive = false,
                targetGameForeground = false,
                overlayForegroundServiceActive = true,
            ),
        )
    }

    @Test
    fun skipAway_whenOverlayIngamePresenceActive() {
        assertTrue(
            OverlayMainAppPresencePolicy.shouldSkipAwayPing(
                inGameOverlayUiActive = false,
                targetGameForeground = false,
                overlayIngamePresenceActive = true,
            ),
        )
    }

    @Test
    fun skipOnline_whenOverlayForegroundServiceActive() {
        assertTrue(
            OverlayMainAppPresencePolicy.shouldSkipOnlinePing(
                inGameOverlayUiActive = false,
                targetGameForeground = false,
                overlayForegroundServiceActive = true,
            ),
        )
    }

    @Test
    fun skipOnline_whenOverlayIngamePresenceActive() {
        assertTrue(
            OverlayMainAppPresencePolicy.shouldSkipOnlinePing(
                inGameOverlayUiActive = false,
                targetGameForeground = false,
                overlayIngamePresenceActive = true,
            ),
        )
    }
}
