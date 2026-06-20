package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayHudChromeRestorePolicyTest {
    @Test
    fun restore_whenInGameAndPanelClosed() {
        assertTrue(
            OverlayHudChromeRestorePolicy.shouldRestoreInGameWindowVisibility(
                inGameActive = true,
                holdActive = false,
                fullscreenPanelObscuring = false,
            ),
        )
    }

    @Test
    fun restore_whenHoldActiveAfterPanelClose() {
        assertTrue(
            OverlayHudChromeRestorePolicy.shouldRestoreInGameWindowVisibility(
                inGameActive = false,
                holdActive = true,
                fullscreenPanelObscuring = false,
            ),
        )
    }

    @Test
    fun noRestore_whenFullscreenPanelStillObscuring() {
        assertFalse(
            OverlayHudChromeRestorePolicy.shouldRestoreInGameWindowVisibility(
                inGameActive = true,
                holdActive = true,
                fullscreenPanelObscuring = true,
            ),
        )
    }

    @Test
    fun noRestore_whenNotInGameAndNoHold() {
        assertFalse(
            OverlayHudChromeRestorePolicy.shouldRestoreInGameWindowVisibility(
                inGameActive = false,
                holdActive = false,
                fullscreenPanelObscuring = false,
            ),
        )
    }
}
