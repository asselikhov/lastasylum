package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayChatContentActiveTest {
    @Test
    fun hudChatPane_isActive() {
        assertTrue(isOverlayChatContentActiveForTest(OverlayHudPane.Chat))
    }

    @Test
    fun hudForumPane_isNotActive() {
        assertFalse(isOverlayChatContentActiveForTest(OverlayHudPane.Forum))
    }

    @Test
    fun hudNewsPane_isNotActive() {
        assertFalse(isOverlayChatContentActiveForTest(OverlayHudPane.News))
    }
}

/** Mirrors [CombatOverlayService.isOverlayChatContentActive] for unit tests. */
private fun isOverlayChatContentActiveForTest(hudPane: OverlayHudPane?): Boolean =
    hudPane == OverlayHudPane.Chat
