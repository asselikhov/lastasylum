package com.lastasylum.alliance.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayHudBadgeAtomicRefreshTest {
    @Test
    fun fullRefreshResult_replacesAllChipsAtomically() {
        val flow = MutableStateFlow(
            OverlayGameStatusHudState(
                allianceChatUnread = 5,
                teamNewsUnread = 3,
                forumUnread = 2,
            ),
        )
        val coordinator = OverlayInboxBadgeCoordinator()
        val reducer = OverlayHudBadgeReducer(flow)
        val bus = OverlayHudBadgeBus(
            reducer = reducer,
            mergeNews = { auth, prev, useAuth ->
                coordinator.mergeHudNews(auth, prev, useAuth)
            },
            mergeForum = { auth, prev, raw, useAuth ->
                coordinator.mergeHudForum(auth, prev, useAuth, raw)
            },
        )
        bus.emit(
            OverlayHudBadgeEvent.FullRefreshResult(
                allianceChatUnread = 1,
                teamNewsUnread = 0,
                forumUnread = 4,
                gameSearchEnabled = true,
            ),
        )
        ShadowLooper.idleMainLooper()
        val state = bus.current()
        assertEquals(1, state.allianceChatUnread)
        assertEquals(0, state.teamNewsUnread)
        assertEquals(4, state.forumUnread)
        assertTrue(state.gameSearchEnabled)
    }

    @Test
    fun partialEventsInOneBatch_stillCoalescePerFrame() {
        val flow = MutableStateFlow(OverlayGameStatusHudState())
        val coordinator = OverlayInboxBadgeCoordinator()
        val reducer = OverlayHudBadgeReducer(flow)
        val bus = OverlayHudBadgeBus(
            reducer = reducer,
            mergeNews = { auth, prev, useAuth ->
                coordinator.mergeHudNews(auth, prev, useAuth)
            },
            mergeForum = { auth, prev, raw, useAuth ->
                coordinator.mergeHudForum(auth, prev, useAuth, raw)
            },
        )
        bus.emit(OverlayHudBadgeEvent.HubUnread(2))
        bus.emit(OverlayHudBadgeEvent.NewsUnread(1))
        bus.emit(OverlayHudBadgeEvent.ForumUnread(3, rawServer = 3))
        ShadowLooper.idleMainLooper()
        val state = bus.current()
        assertEquals(2, state.allianceChatUnread)
        assertEquals(1, state.teamNewsUnread)
        assertEquals(3, state.forumUnread)
    }
}
