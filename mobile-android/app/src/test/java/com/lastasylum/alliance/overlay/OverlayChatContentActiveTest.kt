package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayChatContentActiveTest {
    @Test
    fun hudChatPane_isActive() {
        assertTrue(isOverlayChatContentActiveForTest(OverlayHudPane.Chat, teamTabIndex = 1))
    }

    @Test
    fun hudForumPane_isNotActive() {
        assertFalse(isOverlayChatContentActiveForTest(OverlayHudPane.Forum, teamTabIndex = 0))
    }

    @Test
    fun legacyPanel_tabZero_isActive() {
        assertTrue(isOverlayChatContentActiveForTest(hudPane = null, teamTabIndex = 0))
    }

    @Test
    fun legacyPanel_nonChatTab_isNotActive() {
        assertFalse(isOverlayChatContentActiveForTest(hudPane = null, teamTabIndex = 1))
    }
}

/** Mirrors [CombatOverlayService.isOverlayChatContentActive] for unit tests. */
private fun isOverlayChatContentActiveForTest(
    hudPane: OverlayHudPane?,
    teamTabIndex: Int,
): Boolean = when (hudPane) {
    OverlayHudPane.Chat -> true
    OverlayHudPane.Forum,
    OverlayHudPane.News,
    OverlayHudPane.Participants,
    OverlayHudPane.Notifications,
    -> false
    null -> teamTabIndex == 0
}
