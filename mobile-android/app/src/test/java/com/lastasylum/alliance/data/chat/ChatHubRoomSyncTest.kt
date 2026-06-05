package com.lastasylum.alliance.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatHubRoomSyncTest {
    @Test
    fun allianceHubRoom_picksSortOrderOnePtScope() {
        val hub = room(id = "hub", sortOrder = 1, allianceId = "pt:team1")
        val raid = room(id = "raid", sortOrder = 2, allianceId = "pt:team1", title = "Рейд")
        val picked = ChatHubRoomSync.allianceHubRoom(listOf(raid, hub))
        assertEquals("hub", picked?.id)
    }

    @Test
    fun allianceHubRoom_picksAllianceNameScope() {
        val hub = room(id = "hub", sortOrder = 1, allianceId = "SquadRelay", title = "Альянс")
        assertEquals("hub", ChatHubRoomSync.allianceHubRoom(listOf(hub))?.id)
    }

    @Test
    fun isAllianceHubRoom_rejectsRaidAndGlobal() {
        assertFalse(ChatHubRoomSync.isAllianceHubRoom(room(id = "r", sortOrder = 2, allianceId = "pt:1", title = "Рейд")))
        assertFalse(ChatHubRoomSync.isAllianceHubRoom(room(id = "g", sortOrder = 1, allianceId = ChatAllianceIds.GLOBAL)))
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
