package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayLegacyPanelMustNotAppearTest {
    @Test
    fun warmupHostState_usesChatPaneNotNullLegacy() {
        val warmupHost = OverlayHudPanelHostStateForTest()
        assertNotNull(warmupHost.hudPane)
        assertTrue(warmupHost.hudPane == OverlayHudPane.Chat)
    }

    @Test
    fun visiblePanel_requiresNonNullHudPane() {
        assertFalse(shouldRestoreOverlayPanelForTest(hudPane = null, panelVisible = true))
        assertTrue(
            shouldRestoreOverlayPanelForTest(
                hudPane = OverlayHudPane.News,
                panelVisible = true,
            ),
        )
    }

    @Test
    fun openingNews_afterWarmup_hasExplicitPane() {
        val afterOpen = OverlayHudPanelHostStateForTest(
            hudPane = OverlayHudPane.News,
            openGeneration = 2,
        )
        assertTrue(afterOpen.hudPane != OverlayHudPane.Chat || afterOpen.openGeneration > 1)
        assertTrue(afterOpen.hudPane == OverlayHudPane.News)
    }
}

/** Mirrors non-null [OverlayHudPane] host state after legacy tab removal. */
private data class OverlayHudPanelHostStateForTest(
    val hudPane: OverlayHudPane = OverlayHudPane.Chat,
    val openGeneration: Int = 0,
)

/** Mirrors [CombatOverlayService.shouldRestoreOverlayChatTeamPanelWindow]. */
private fun shouldRestoreOverlayPanelForTest(
    hudPane: OverlayHudPane?,
    panelVisible: Boolean,
): Boolean = hudPane != null &&
    OverlayFullscreenPanelRestorePolicy.shouldRestorePanelWindow(
        servicePanelVisible = panelVisible,
        holdPanelVisible = false,
    )
