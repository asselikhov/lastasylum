package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayHudPanelSessionTest {
    @Test
    fun applyCloseCleanup_whenSessionMatchesAndPanelHidden() {
        assertTrue(
            OverlayHudPanelSession.shouldApplyCloseCleanup(
                closingSession = 3,
                currentSession = 3,
                panelVisible = false,
            ),
        )
    }

    @Test
    fun skipCloseCleanup_whenPanelReopened() {
        assertFalse(
            OverlayHudPanelSession.shouldApplyCloseCleanup(
                closingSession = 3,
                currentSession = 4,
                panelVisible = true,
            ),
        )
    }

    @Test
    fun skipCloseCleanup_whenStaleSessionEvenIfPanelHidden() {
        assertFalse(
            OverlayHudPanelSession.shouldApplyCloseCleanup(
                closingSession = 2,
                currentSession = 5,
                panelVisible = false,
            ),
        )
    }
}
