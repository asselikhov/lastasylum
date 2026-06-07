package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPresenceCoordinatorTest {
    @Test
    fun keepIngamePing_false_whenOnlyOverlaySessionActive_withoutInGameProbe() {
        assertFalse(
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
    fun keepIngamePing_false_whenOnlyParticipantsPanelVisible() {
        assertFalse(
            computeOverlayKeepIngamePing(
                inGameProbe = false,
                overlaySessionActive = false,
                inGameOverlayUiActive = false,
                isVoiceActive = false,
                isOnlineParticipantsPanelVisible = true,
            ),
        )
    }

    @Test
    fun keepIngamePing_whenVoiceActive() {
        assertTrue(
            computeOverlayKeepIngamePing(
                inGameProbe = false,
                overlaySessionActive = false,
                inGameOverlayUiActive = false,
                isVoiceActive = true,
                isOnlineParticipantsPanelVisible = false,
            ),
        )
    }
}
