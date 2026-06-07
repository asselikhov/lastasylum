package com.lastasylum.alliance.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Panel-close badge refresh: mark-read → authoritative zero must not re-bump from stale raw counts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayHudBadgePanelCloseTest {
    private fun busWithCoordinator(): Pair<OverlayHudBadgeBus, OverlayInboxBadgeCoordinator> {
        val flow = MutableStateFlow(OverlayGameStatusHudState())
        val reducer = OverlayHudBadgeReducer(flow)
        val coordinator = OverlayInboxBadgeCoordinator()
        val bus = OverlayHudBadgeBus(
            reducer = reducer,
            mergeNews = coordinator::mergeHudNews,
            mergeForum = { effective, prev, raw, useAuthoritative ->
                coordinator.mergeHudForum(effective, prev, useAuthoritative, raw)
            },
        )
        return bus to coordinator
    }

    @Test
    fun panelClose_newsMarkedRead_authoritativeZeroStaysZero() {
        val (bus, coordinator) = busWithCoordinator()
        coordinator.bumpNewsOptimistic(2)
        bus.emit(OverlayHudBadgeEvent.NewsUnread(2, useAuthoritative = false))
        ShadowLooper.idleMainLooper()
        assertEquals(2, bus.current().teamNewsUnread)

        coordinator.clearNewsOptimistic()
        bus.emit(OverlayHudBadgeEvent.NewsUnread(0, useAuthoritative = true))
        ShadowLooper.idleMainLooper()
        assertEquals(0, bus.current().teamNewsUnread)

        bus.emit(OverlayHudBadgeEvent.NewsUnread(0, useAuthoritative = true))
        ShadowLooper.idleMainLooper()
        assertEquals(0, bus.current().teamNewsUnread)
    }

    @Test
    fun panelClose_forumMarkedRead_staleRawDoesNotReBump() {
        val (bus, coordinator) = busWithCoordinator()
        coordinator.bumpForumOptimistic(3)
        bus.emit(OverlayHudBadgeEvent.ForumUnread(3, rawServer = 3, useAuthoritative = false))
        ShadowLooper.idleMainLooper()
        assertEquals(3, bus.current().forumUnread)

        coordinator.clearForumOptimistic()
        bus.emit(
            OverlayHudBadgeEvent.ForumUnread(
                effective = 0,
                rawServer = 4,
                useAuthoritative = true,
            ),
        )
        ShadowLooper.idleMainLooper()
        assertEquals(0, bus.current().forumUnread)
    }

    @Test
    fun panelClose_forumListWithoutMarkRead_keepsOptimisticBadge() {
        val (bus, coordinator) = busWithCoordinator()
        coordinator.bumpForumOptimistic(3)
        bus.emit(OverlayHudBadgeEvent.ForumUnread(3, rawServer = 3, useAuthoritative = false))
        ShadowLooper.idleMainLooper()
        assertEquals(3, bus.current().forumUnread)

        // Closing forum list without viewport mark-read must not clear optimistic floor.
        bus.emit(
            OverlayHudBadgeEvent.ForumUnread(
                effective = 3,
                rawServer = 3,
                useAuthoritative = true,
            ),
        )
        ShadowLooper.idleMainLooper()
        assertEquals(3, bus.current().forumUnread)
    }

    @Test
    fun panelClose_fullRefreshAfterMarkRead_keepsZeroBadges() {
        val (bus, coordinator) = busWithCoordinator()
        coordinator.bumpNewsOptimistic(1)
        coordinator.bumpForumOptimistic(1)
        bus.emit(OverlayHudBadgeEvent.NewsUnread(1, useAuthoritative = false))
        bus.emit(OverlayHudBadgeEvent.ForumUnread(1, rawServer = 1, useAuthoritative = false))
        ShadowLooper.idleMainLooper()

        coordinator.clearNewsOptimistic()
        coordinator.clearForumOptimistic()
        bus.emit(
            OverlayHudBadgeEvent.FullRefreshResult(
                allianceChatUnread = 0,
                teamNewsUnread = 0,
                forumUnread = 0,
            ),
        )
        ShadowLooper.idleMainLooper()
        assertEquals(0, bus.current().teamNewsUnread)
        assertEquals(0, bus.current().forumUnread)
    }
}
