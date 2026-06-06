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
class OverlayHudBadgeBusTest {
    @Test
    fun emit_coalescesMultipleEventsInOneCommit() {
        val flow = MutableStateFlow(OverlayGameStatusHudState())
        val reducer = OverlayHudBadgeReducer(flow)
        val bus = OverlayHudBadgeBus(
            reducer = reducer,
            mergeNews = { auth, prev, _ -> maxOf(auth, prev) },
            mergeForum = { auth, prev, _, _ -> maxOf(auth, prev) },
        )
        bus.emit(OverlayHudBadgeEvent.HubUnread(3))
        bus.emit(OverlayHudBadgeEvent.NewsUnread(2))
        bus.emit(OverlayHudBadgeEvent.ForumUnread(1))
        ShadowLooper.idleMainLooper()
        assertEquals(3, flow.value.allianceChatUnread)
        assertEquals(2, flow.value.teamNewsUnread)
        assertEquals(1, flow.value.forumUnread)
    }

    @Test
    fun emit_laterHubUnreadWinsWithinBatch() {
        val flow = MutableStateFlow(OverlayGameStatusHudState(allianceChatUnread = 5))
        val reducer = OverlayHudBadgeReducer(flow)
        val bus = OverlayHudBadgeBus(
            reducer = reducer,
            mergeNews = { auth, prev, _ -> maxOf(auth, prev) },
            mergeForum = { auth, prev, _, _ -> maxOf(auth, prev) },
        )
        bus.emit(OverlayHudBadgeEvent.HubUnread(1))
        bus.emit(OverlayHudBadgeEvent.HubUnread(4))
        ShadowLooper.idleMainLooper()
        assertEquals(4, flow.value.allianceChatUnread)
    }

    @Test
    fun emit_appUpdateUrl_setsAndClearsDownloadUrl() {
        val flow = MutableStateFlow(OverlayGameStatusHudState())
        val reducer = OverlayHudBadgeReducer(flow)
        val bus = OverlayHudBadgeBus(
            reducer = reducer,
            mergeNews = { auth, prev, _ -> maxOf(auth, prev) },
            mergeForum = { auth, prev, _, _ -> maxOf(auth, prev) },
        )
        bus.emit(OverlayHudBadgeEvent.AppUpdateUrl("https://example.com/app.apk"))
        ShadowLooper.idleMainLooper()
        assertEquals("https://example.com/app.apk", flow.value.appUpdateDownloadUrl)

        bus.emit(OverlayHudBadgeEvent.AppUpdateUrl(null))
        ShadowLooper.idleMainLooper()
        assertEquals(null, flow.value.appUpdateDownloadUrl)
    }

    @Test
    fun emit_clearHub_resetsAllianceUnread() {
        val flow = MutableStateFlow(OverlayGameStatusHudState(allianceChatUnread = 9))
        val reducer = OverlayHudBadgeReducer(flow)
        val bus = OverlayHudBadgeBus(
            reducer = reducer,
            mergeNews = { auth, prev, _ -> maxOf(auth, prev) },
            mergeForum = { auth, prev, _, _ -> maxOf(auth, prev) },
        )
        bus.emit(OverlayHudBadgeEvent.ClearHub)
        ShadowLooper.idleMainLooper()
        assertEquals(0, flow.value.allianceChatUnread)
    }
}
