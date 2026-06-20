package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayFullscreenPanelObscuringPolicyTest {
    @Test
    fun obscuring_whenServicePanelVisible() {
        assertTrue(
            OverlayFullscreenPanelObscuringPolicy.isObscuring(
                servicePanelVisible = true,
                holdPanelVisible = false,
                panelRootVisible = false,
                hasActiveHudPane = false,
            ),
        )
    }

    @Test
    fun notObscuring_whenRootVisibleButSessionClosed() {
        assertFalse(
            OverlayFullscreenPanelObscuringPolicy.isObscuring(
                servicePanelVisible = false,
                holdPanelVisible = false,
                panelRootVisible = true,
                hasActiveHudPane = false,
            ),
        )
    }

    @Test
    fun obscuring_whenRootVisibleAndPaneActive() {
        assertTrue(
            OverlayFullscreenPanelObscuringPolicy.isObscuring(
                servicePanelVisible = false,
                holdPanelVisible = false,
                panelRootVisible = true,
                hasActiveHudPane = true,
            ),
        )
    }
}
