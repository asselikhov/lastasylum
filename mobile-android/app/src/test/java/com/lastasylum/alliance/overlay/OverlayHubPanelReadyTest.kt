package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayHubPanelReadyTest {

    @Test
    fun hasRecentDiskSeed_falseWhenNeverSeeded() {
        OverlayGameStatusHudRefresh.invalidateNewsForumCache()
        assertFalse(OverlayGameStatusHudRefresh.hasRecentDiskSeed())
    }

    @Test
    fun hasRecentDiskSeed_trueAfterDiskSeed() {
        OverlayGameStatusHudRefresh.seedBadgesFromDisk("team-1", newsUnread = 1, forumUnread = 2)
        assertTrue(OverlayGameStatusHudRefresh.hasRecentDiskSeed())
    }
}
