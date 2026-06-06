package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPresenceCoordinatorTest {
    @Test
    fun keepIngamePing_whenOverlaySessionActive_withoutInGameProbe() {
        assertTrue(
            computeOverlayKeepIngamePing(
                inGameProbe = false,
                overlaySessionActive = true,
                inGameOverlayUiActive = false,
                isVoiceActive = false,
                isOnlineParticipantsPanelVisible = false,
            ),
        )
    }

    @Test
    fun keepIngamePing_false_whenNoActiveSignals() {
        assertFalse(
            computeOverlayKeepIngamePing(
                inGameProbe = false,
                overlaySessionActive = false,
                inGameOverlayUiActive = false,
                isVoiceActive = false,
                isOnlineParticipantsPanelVisible = false,
            ),
        )
    }

    @Test
    fun keepIngamePing_whenParticipantsPanelVisible() {
        assertTrue(
            computeOverlayKeepIngamePing(
                inGameProbe = false,
                overlaySessionActive = false,
                inGameOverlayUiActive = false,
                isVoiceActive = false,
                isOnlineParticipantsPanelVisible = true,
            ),
        )
    }
}
