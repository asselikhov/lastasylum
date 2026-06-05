package com.lastasylum.alliance.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRoomKindResolverTest {
    @Test
    fun hub_picksSortOrderOnePtScope() {
        val hub = room(id = "hub", sortOrder = 1, allianceId = "pt:team1")
        val raid = room(id = "raid", sortOrder = 2, allianceId = "pt:team1", title = "Рейд")
        assertEquals("hub", ChatRoomKindResolver.allianceHubRoom(listOf(raid, hub))?.id)
        assertEquals(ChatRoomKind.AllianceHub, ChatRoomKindResolver.kindOf(hub))
    }

    @Test
    fun raid_bySortOrderOrTitle() {
        val byOrder = room(id = "r1", sortOrder = 2, allianceId = "pt:1", title = "X")
        val byTitle = room(id = "r2", sortOrder = 9, allianceId = "pt:1", title = "Рейд")
        assertTrue(ChatRoomKindResolver.isAllianceRaidRoom(byOrder))
        assertTrue(ChatRoomKindResolver.isAllianceRaidRoom(byTitle))
        assertEquals(ChatRoomKind.Raid, ChatRoomKindResolver.kindOf(byTitle))
    }

    @Test
    fun hub_picksSortOrderOneAllianceNameScope() {
        val hub = room(id = "hub", sortOrder = 1, allianceId = "SquadRelay", title = "Альянс")
        assertTrue(ChatRoomKindResolver.isAllianceHubRoom(hub))
        assertEquals("hub", ChatRoomKindResolver.allianceHubRoom(listOf(hub))?.id)
        assertEquals(ChatRoomKind.AllianceHub, ChatRoomKindResolver.kindOf(hub))
    }

    @Test
    fun hub_rejectsRaidAndGlobal() {
        assertFalse(
            ChatRoomKindResolver.isAllianceHubRoom(
                room(id = "r", sortOrder = 2, allianceId = "pt:1", title = "Рейд"),
            ),
        )
        assertFalse(
            ChatRoomKindResolver.isAllianceHubRoom(
                room(id = "g", sortOrder = 1, allianceId = ChatAllianceIds.GLOBAL),
            ),
        )
    }

    private fun room(
        id: String,
        sortOrder: Int,
        allianceId: String,
        title: String = "Hub",
    ) = ChatRoomDto(
        id = id,
        allianceId = allianceId,
        title = title,
        sortOrder = sortOrder,
        unreadCount = 0,
        lastReadMessageId = null,
    )
}
