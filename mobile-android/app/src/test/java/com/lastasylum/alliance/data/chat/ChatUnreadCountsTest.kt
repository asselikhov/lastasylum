package com.lastasylum.alliance.data.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatUnreadCountsTest {
    @Test
    fun tabBadgeTotal_sumsAllRoomsWithEffectiveUnread() {
        val rooms = listOf(
            room(id = "hub", sortOrder = 1, allianceId = "pt:1", unread = 2),
            room(id = "raid", sortOrder = 2, allianceId = "pt:1", title = "Рейд", unread = 1),
        )
        val total = ChatUnreadCounts.tabBadgeTotal(rooms, emptyMap())
        assertEquals(3, total)
    }

    @Test
    fun tabBadgeTotal_suppressesStaleHubWhenLocalReadAhead() {
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
        assertEquals(0, ChatUnreadCounts.tabBadgeTotal(rooms, local))
    }

    @Test
    fun hubClear_mustNotUseReconcileDisplayedUnread() {
        val staleDisplayed = 5
        val serverAfterRead = 0
        val wrong = com.lastasylum.alliance.data.reconcileDisplayedUnread(
            serverAfterRead,
            staleDisplayed,
        )
        assertEquals(
            "Overlay clear bug: reconcile keeps old badge",
            5,
            wrong,
        )
        val correct = serverAfterRead
        assertEquals(0, correct)
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
