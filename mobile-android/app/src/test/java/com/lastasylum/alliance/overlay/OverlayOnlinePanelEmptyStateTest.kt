package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayOnlinePanelEmptyStateTest {
    private val counts = OverlayOnlineFilterCounts(all = 5, ingame = 3, withMic = 1, recent = 2)

    @Test
    fun emptyMessage_nullWhenItemsVisible() {
        assertNull(
            resolveOverlayOnlinePanelEmptyMessageRes(
                activeFilterChip = OverlayOnlineFilterChip.IngameOnly,
                searchQuery = "",
                filterCounts = counts,
                totalVisible = 2,
            ),
        )
    }

    @Test
    fun emptyMessage_search() {
        assertEquals(
            R.string.overlay_online_empty_search,
            resolveOverlayOnlinePanelEmptyMessageRes(
                activeFilterChip = OverlayOnlineFilterChip.All,
                searchQuery = "zzz",
                filterCounts = counts,
                totalVisible = 0,
            ),
        )
    }

    @Test
    fun emptyMessage_micFilter() {
        assertEquals(
            R.string.overlay_online_empty_mic,
            resolveOverlayOnlinePanelEmptyMessageRes(
                activeFilterChip = OverlayOnlineFilterChip.WithMic,
                searchQuery = "",
                filterCounts = counts,
                totalVisible = 0,
            ),
        )
    }

    @Test
    fun emptyMessage_recentFilter() {
        assertEquals(
            R.string.overlay_online_empty_recent,
            resolveOverlayOnlinePanelEmptyMessageRes(
                activeFilterChip = OverlayOnlineFilterChip.RecentOnly,
                searchQuery = "",
                filterCounts = counts,
                totalVisible = 0,
            ),
        )
    }

    @Test
    fun filterCounts_fromSections() {
        val sections = listOf(
            OverlayOnlinePresenceSection(
                kind = PresenceSectionKind.Ingame,
                items = listOf(
                    memberUi("u1"),
                    memberUi("u2"),
                ),
            ),
            OverlayOnlinePresenceSection(
                kind = PresenceSectionKind.Recent,
                items = listOf(memberUi("u3")),
            ),
        )
        val voice = mapOf("u1" to VoiceMemberFlags(micOn = true, soundOn = false))
        val result = computeOverlayOnlineFilterCounts(sections, voice)
        assertEquals(3, result.all)
        assertEquals(2, result.ingame)
        assertEquals(1, result.withMic)
        assertEquals(1, result.recent)
    }

    @Test
    fun filterChipCountFor_mapsCorrectly() {
        assertEquals(5, filterChipCountFor(OverlayOnlineFilterChip.All, counts))
        assertEquals(3, filterChipCountFor(OverlayOnlineFilterChip.IngameOnly, counts))
        assertEquals(1, filterChipCountFor(OverlayOnlineFilterChip.WithMic, counts))
        assertEquals(2, filterChipCountFor(OverlayOnlineFilterChip.RecentOnly, counts))
    }

    private fun memberUi(userId: String) = OverlayOnlineMemberUiModel(
        userId = userId,
        username = userId,
        avatarRelativeUrl = null,
        teamRole = "R1",
        isLeader = false,
        presenceStatus = "ingame",
        lastPresenceAt = null,
        isSelf = false,
        inGameNow = true,
        freshness = PresenceFreshness.Fresh,
    )
}
