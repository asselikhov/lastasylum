package com.lastasylum.alliance.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUnreadCountsTest {
    @Test
    fun tabBadgeTotal_sumsDisplayedRoomUnread() {
        val rooms = listOf(
            room(id = "hub", sortOrder = 1, allianceId = "pt:1", unread = 2),
            room(id = "raid", sortOrder = 2, allianceId = "pt:1", title = "Рейд", unread = 1),
        )
        assertEquals(3, ChatUnreadCounts.tabBadgeTotal(rooms))
    }

    @Test
    fun tabBadgeTotalFromServer_suppressesStaleHubWhenLocalReadAhead() {
        val rooms = listOf(
            room(
                id = "hub",
                sortOrder = 1,
                allianceId = "pt:team1",
                unread = 3,
                lastRead = "000000000000000000000040",
            ),
        )
        val local = mapOf("hub" to "000000000000000000000080")
        assertEquals(0, ChatUnreadCounts.tabBadgeTotalFromServer(rooms, local))
    }

    @Test
    fun allianceHubDisplayUnread_readsMergedHubChip() {
        val rooms = listOf(
            room(id = "hub", sortOrder = 1, allianceId = "pt:1", unread = 4),
            room(id = "raid", sortOrder = 2, allianceId = "pt:1", unread = 9),
        )
        assertEquals(4, ChatUnreadCounts.allianceHubDisplayUnread(rooms))
    }

    @Test
    fun overlayAllianceHubBadge_honorsFloorWhenServerUnreadZero() {
        val rooms = listOf(
            room(id = "hub", sortOrder = 1, allianceId = "pt:team1", unread = 0),
        )
        assertEquals(
            4,
            ChatUnreadCounts.overlayAllianceHubBadge(
                rooms = rooms,
                localReadByRoom = emptyMap(),
                optimisticFloor = 4,
                previouslyDisplayed = 2,
            ),
        )
    }

    @Test
    fun isAllianceHubLocallyReadSuppressed_whenLocalCursorAhead() {
        val rooms = listOf(
            room(
                id = "hub",
                sortOrder = 1,
                allianceId = "pt:team1",
                unread = 6,
                lastRead = "000000000000000000000040",
            ),
        )
        val local = mapOf("hub" to "000000000000000000000090")
        assertTrue(ChatUnreadCounts.isAllianceHubLocallyReadSuppressed(rooms, local))
    }

    @Test
    fun overlayAllianceHubBadge_zeroWhenReadLocally_despiteFloorAndPrevious() {
        val rooms = listOf(
            room(
                id = "hub",
                sortOrder = 1,
                allianceId = "pt:team1",
                unread = 6,
                lastRead = "000000000000000000000040",
            ),
        )
        val local = mapOf("hub" to "000000000000000000000090")
        assertEquals(
            0,
            ChatUnreadCounts.overlayAllianceHubBadge(
                rooms = rooms,
                localReadByRoom = local,
                optimisticFloor = 4,
                previouslyDisplayed = 5,
            ),
        )
    }

    @Test
    fun overlayAllianceHubBadge_appSyncPath_zeroWhenLocallyRead_despiteStaleDtoUnread() {
        val rooms = listOf(
            room(
                id = "hub",
                sortOrder = 1,
                allianceId = "pt:team1",
                unread = 8,
                lastRead = "000000000000000000000040",
            ),
        )
        val local = mapOf("hub" to "000000000000000000000100")
        assertEquals(
            0,
            ChatUnreadCounts.overlayAllianceHubBadge(
                rooms = rooms,
                localReadByRoom = local,
                optimisticFloor = 0,
                previouslyDisplayed = 0,
            ),
        )
        assertEquals(8, ChatUnreadCounts.allianceHubDisplayUnread(rooms))
    }

    @Test
    fun overlayAllianceHubBadge_ignoresRaidRoomUnread() {
        val rooms = listOf(
            room(id = "hub", sortOrder = 1, allianceId = "pt:team1", unread = 2),
            room(id = "raid", sortOrder = 2, allianceId = "pt:team1", title = "Рейд", unread = 7),
        )
        assertEquals(2, ChatUnreadCounts.overlayAllianceHubBadge(rooms, emptyMap()))
        assertEquals(9, ChatUnreadCounts.tabBadgeTotal(rooms))
    }

    @Test
    fun allianceHubUnread_allianceNameScope() {
        val rooms = listOf(
            room(
                id = "hub",
                sortOrder = 1,
                allianceId = "SquadRelay",
                title = "Альянс",
                unread = 3,
            ),
            room(
                id = "raid",
                sortOrder = 2,
                allianceId = "pt:team1",
                title = "Рейд",
                unread = 9,
            ),
        )
        assertEquals(3, ChatUnreadCounts.allianceHubUnread(rooms))
        assertEquals(3, ChatUnreadCounts.overlayAllianceHubBadge(rooms, emptyMap()))
    }

    @Test
    fun hubClear_mustNotUseReconcileDisplayedUnread() {
        assertEquals(
            0,
            com.lastasylum.alliance.data.displayedUnreadCount(
                effectiveUnread = 0,
                previouslyDisplayed = 5,
                rawServerUnread = 2,
            ),
        )
    }

    private fun room(
        id: String,
        sortOrder: Int = 0,
        allianceId: String = "",
        unread: Int = 0,
        lastRead: String? = null,
        title: String = id,
    ): ChatRoomDto = ChatRoomDto(
        id = id,
        allianceId = allianceId,
        title = title,
        sortOrder = sortOrder,
        unreadCount = unread,
        lastReadMessageId = lastRead,
    )
}
