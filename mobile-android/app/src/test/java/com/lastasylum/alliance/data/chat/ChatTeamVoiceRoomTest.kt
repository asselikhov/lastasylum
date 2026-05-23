package com.lastasylum.alliance.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChatTeamVoiceRoomTest {
    @Test
    fun socketRoomId_isTeamSentinel() {
        assertEquals("team", ChatTeamVoiceRoom.SOCKET_ROOM_ID)
    }

    @Test
    fun teamVoiceChannel_resolvesToAllianceHubNotRaid() {
        val hub = room(id = "hub", sortOrder = 1, allianceId = "pt:team1")
        val raid = room(id = "raid", sortOrder = 2, allianceId = "pt:team1", title = "Рейд")
        val rooms = listOf(raid, hub)
        assertEquals("hub", ChatHubRoomSync.allianceHubRoom(rooms)?.id)
        assertNotEquals(
            ChatRoomKindResolver.allianceRaidRoom(rooms)?.id,
            ChatHubRoomSync.allianceHubRoom(rooms)?.id,
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
