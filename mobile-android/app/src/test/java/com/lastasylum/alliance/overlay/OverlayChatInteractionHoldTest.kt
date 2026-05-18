package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayChatInteractionHoldTest {
    @Test
    fun pickerSession_blocksGateUntilEnded() {
        OverlayChatInteractionHold.endOverlaySystemPickerSession()
        assertFalse(OverlayChatInteractionHold.isOverlaySystemPickerSessionActive())
        OverlayChatInteractionHold.beginOverlaySystemPickerSession()
        assertTrue(OverlayChatInteractionHold.isOverlaySystemPickerSessionActive())
        assertTrue(OverlayChatInteractionHold.isGameForegroundGateSuppressed())
        OverlayChatInteractionHold.endOverlaySystemPickerSession()
        assertFalse(OverlayChatInteractionHold.isOverlaySystemPickerSessionActive())
    }
}
