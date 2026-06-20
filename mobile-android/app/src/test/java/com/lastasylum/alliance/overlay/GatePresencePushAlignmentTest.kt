package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatePresencePushAlignmentTest {
    @Test
    fun ingameHeartbeat_onlyWhenInGameAndStableShow() {
        assertTrue(heartbeatAllowed(inGame = true, stableShow = true))
        assertFalse(heartbeatAllowed(inGame = true, stableShow = false))
        assertFalse(heartbeatAllowed(inGame = false, stableShow = true))
        assertFalse(heartbeatAllowed(inGame = false, stableShow = false))
    }

    private fun heartbeatAllowed(inGame: Boolean, stableShow: Boolean): Boolean =
        inGame && stableShow
}
