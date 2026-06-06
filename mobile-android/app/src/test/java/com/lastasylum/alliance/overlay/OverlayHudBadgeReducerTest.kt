package com.lastasylum.alliance.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayHudBadgeReducerTest {
    @Test
    fun commitInboxPartialRefresh_appliesAllChipsFromSinglePrevSnapshot() {
        val flow = MutableStateFlow(
            OverlayGameStatusHudState(
                allianceChatUnread = 3,
                teamNewsUnread = 2,
                forumUnread = 1,
            ),
        )
        val reducer = OverlayHudBadgeReducer(flow)
        reducer.commitInboxPartialRefresh(
            allianceChatUnread = 0,
            teamNewsUnread = 5,
            forumUnread = 4,
        )
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
    fun hubZeroImmediatePaint_replacesDisplayedCount() {
        val flow = MutableStateFlow(OverlayGameStatusHudState(allianceChatUnread = 7))
        val reducer = OverlayHudBadgeReducer(flow)
        reducer.commitInboxPartialRefresh(allianceChatUnread = 0)
        assertEquals(0, flow.value.allianceChatUnread)
    }
}
