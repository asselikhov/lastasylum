package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayInboxBadgeCoordinatorTest {
    @Test
    fun mergeHudForum_authoritativeZero_doesNotAffectNewsMerge() {
        val coordinator = OverlayInboxBadgeCoordinator()
        coordinator.bumpForumOptimistic(2)
        val forum = coordinator.mergeHudForum(
            authoritative = 0,
            prevDisplayed = 2,
            useAuthoritative = true,
        )
        assertEquals(2, forum)
    }

    @Test
    fun mergeHudNews_authoritativeDoesNotUseForumFloor() {
        val coordinator = OverlayInboxBadgeCoordinator()
        coordinator.bumpForumOptimistic(5)
        val news = coordinator.mergeHudNews(
            authoritative = 1,
            prevDisplayed = 0,
            useAuthoritative = true,
        )
        assertEquals(1, news)
    }
}
