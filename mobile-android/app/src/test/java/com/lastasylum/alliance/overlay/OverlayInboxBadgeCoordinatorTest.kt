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
    fun mergeHudForum_authoritativeZero_clearsDisplayedWithoutOptimistic() {
        val coordinator = OverlayInboxBadgeCoordinator()
        val merged = coordinator.mergeHudForum(
            authoritative = 0,
            prevDisplayed = 5,
            useAuthoritative = true,
        )
        assertEquals(0, merged)
    }

    @Test
    fun mergeHudNews_partialRefresh_allowsDecrease() {
        val coordinator = OverlayInboxBadgeCoordinator()
        val merged = coordinator.mergeHudNews(
            authoritative = 0,
            prevDisplayed = 4,
            useAuthoritative = false,
        )
        assertEquals(0, merged)
    }

    @Test
    fun mergeForumDisplayed_suppressesStaleRawServerWhenEffectiveZero() {
        val coordinator = OverlayInboxBadgeCoordinator()
        val merged = coordinator.mergeForumDisplayed(
            serverCount = 0,
            previouslyDisplayed = 4,
            rawServerCount = 3,
        )
        assertEquals(0, merged)
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
