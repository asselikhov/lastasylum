package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun mergeHudForum_multiEventOrdering_keepsOptimisticUntilAuthoritativeCatchesUp() {
        val coordinator = OverlayInboxBadgeCoordinator()
        coordinator.bumpForumOptimistic(3)
        val afterPartialZero = coordinator.mergeHudForum(
            authoritative = 0,
            prevDisplayed = 3,
            useAuthoritative = false,
        )
        assertEquals(3, afterPartialZero)
        val afterServerCatchUp = coordinator.mergeHudForum(
            authoritative = 3,
            prevDisplayed = afterPartialZero,
            useAuthoritative = true,
        )
        assertEquals(3, afterServerCatchUp)
    }

    @Test
    fun mergeHudNews_optimisticFloor_keepsDisplayedUntilAuthoritativeCatchUp() {
        val coordinator = OverlayInboxBadgeCoordinator()
        coordinator.bumpNewsOptimistic(3)
        val afterPartialZero = coordinator.mergeHudNews(
            authoritative = 0,
            prevDisplayed = 3,
            useAuthoritative = false,
        )
        assertEquals(3, afterPartialZero)
        val afterServerCatchUp = coordinator.mergeHudNews(
            authoritative = 3,
            prevDisplayed = afterPartialZero,
            useAuthoritative = true,
        )
        assertEquals(3, afterServerCatchUp)
    }

    @Test
    fun bumpNewsOptimistic_setsFloorAndGrace() {
        val coordinator = OverlayInboxBadgeCoordinator()
        coordinator.bumpNewsOptimistic(2)
        assertEquals(2, coordinator.newsOptimisticFloor)
        assertTrue(coordinator.shouldDeferNewsReconcile())
    }
}
