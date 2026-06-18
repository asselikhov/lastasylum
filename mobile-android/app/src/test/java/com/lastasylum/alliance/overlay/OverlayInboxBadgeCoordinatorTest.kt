package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.teams.ForumUnreadCounts
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
    fun mergeForumDisplayed_trustsEffectiveWhenLocalReadSuppressesRaw() {
        val coordinator = OverlayInboxBadgeCoordinator()
        val merged = coordinator.mergeForumDisplayed(
            serverCount = 0,
            previouslyDisplayed = 0,
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

    @Test
    fun invalidateNewsBadgeCachesFully_clearsCoordinatorAndHudRefreshCache() {
        val coordinator = OverlayInboxBadgeCoordinator()
        coordinator.cacheAuthoritativeNews("team1", 3)
        OverlayGameStatusHudRefresh.seedBadgesFromDisk("team1", newsUnread = 3, forumUnread = 0)
        coordinator.invalidateNewsBadgeCachesFully()
        assertEquals(null, coordinator.readCachedNews("team1"))
    }

    @Test
    fun cacheAuthoritative_storesServerNotDisplayed() {
        val coordinator = OverlayInboxBadgeCoordinator()
        coordinator.cacheAuthoritativeNews("team1", 0)
        coordinator.cacheAuthoritativeForum("team1", effective = 0, rawServer = 3)
        assertEquals(0, coordinator.readCachedNews("team1"))
        assertEquals(0, coordinator.readCachedForum("team1"))
        assertEquals(
            ForumUnreadCounts(effective = 0, rawServer = 3),
            coordinator.readCachedForumCounts("team1"),
        )
        coordinator.clearNewsOptimistic()
        val merged = coordinator.mergeHudNews(
            authoritative = coordinator.readCachedNews("team1")!!,
            prevDisplayed = 5,
            useAuthoritative = true,
        )
        assertEquals(0, merged)
    }
}
