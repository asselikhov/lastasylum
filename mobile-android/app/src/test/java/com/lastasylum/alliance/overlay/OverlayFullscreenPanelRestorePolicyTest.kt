package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayFullscreenPanelRestorePolicyTest {
    @Test
    fun restore_whenServiceMarksPanelVisible() {
        assertTrue(
            OverlayFullscreenPanelRestorePolicy.shouldRestorePanelWindow(
                servicePanelVisible = true,
                holdPanelVisible = false,
            ),
        )
    }

    @Test
    fun restore_whenHoldMarksPanelVisible() {
        assertTrue(
            OverlayFullscreenPanelRestorePolicy.shouldRestorePanelWindow(
                servicePanelVisible = false,
                holdPanelVisible = true,
            ),
        )
    }

    @Test
    fun noRestore_afterCloseClearsBothFlags() {
        assertFalse(
            OverlayFullscreenPanelRestorePolicy.shouldRestorePanelWindow(
                servicePanelVisible = false,
                holdPanelVisible = false,
            ),
        )
    }
}
