package com.lastasylum.alliance.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayHudBadgeReducerTest {
    @Test
    fun commit_skipsWriteWhenUnchanged() {
        val flow = MutableStateFlow(OverlayGameStatusHudState(allianceChatUnread = 2))
        val reducer = OverlayHudBadgeReducer(flow)
        var transformCalls = 0
        reducer.commit {
            transformCalls++
            it.copy(allianceChatUnread = 2)
        }
        assertEquals(1, transformCalls)
        assertEquals(2, flow.value.allianceChatUnread)
    }

    @Test
    fun commit_appliesTransformWhenChanged() {
        val flow = MutableStateFlow(OverlayGameStatusHudState(allianceChatUnread = 7))
        val reducer = OverlayHudBadgeReducer(flow)
        reducer.commit { it.copy(allianceChatUnread = 0) }
        assertEquals(0, flow.value.allianceChatUnread)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayHudBadgeBusPartialRefreshTest {
    @Test
    fun bus_partialRefresh_appliesAllChipsFromSingleCommit() {
        val flow = MutableStateFlow(
            OverlayGameStatusHudState(
                allianceChatUnread = 3,
                teamNewsUnread = 2,
                forumUnread = 1,
            ),
        )
        val reducer = OverlayHudBadgeReducer(flow)
        val bus = OverlayHudBadgeBus(
            reducer = reducer,
            mergeNews = { auth, prev, _ -> maxOf(auth, prev) },
            mergeForum = { auth, prev, _, _ -> maxOf(auth, prev) },
        )
        bus.emit(OverlayHudBadgeEvent.HubUnread(0))
        bus.emit(OverlayHudBadgeEvent.NewsUnread(5))
        bus.emit(OverlayHudBadgeEvent.ForumUnread(4))
        ShadowLooper.idleMainLooper()
        assertEquals(
            OverlayGameStatusHudState(
                allianceChatUnread = 0,
                teamNewsUnread = 5,
                forumUnread = 4,
            ),
            flow.value,
        )
    }

    @Test
    fun bus_hubZeroImmediatePaint_replacesDisplayedCount() {
        val flow = MutableStateFlow(OverlayGameStatusHudState(allianceChatUnread = 7))
        val reducer = OverlayHudBadgeReducer(flow)
        val bus = OverlayHudBadgeBus(
            reducer = reducer,
            mergeNews = { auth, prev, _ -> maxOf(auth, prev) },
            mergeForum = { auth, prev, _, _ -> maxOf(auth, prev) },
        )
        bus.emit(OverlayHudBadgeEvent.HubUnread(0))
        ShadowLooper.idleMainLooper()
        assertEquals(0, flow.value.allianceChatUnread)
    }
}
