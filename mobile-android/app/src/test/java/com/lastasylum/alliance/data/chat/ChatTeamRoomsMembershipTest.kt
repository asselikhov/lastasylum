package com.lastasylum.alliance.data.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTeamRoomsMembershipTest {
    private fun room(
        id: String,
        allianceId: String,
        title: String,
        sortOrder: Int,
    ) = ChatRoomDto(
        id = id,
        allianceId = allianceId,
        title = title,
        sortOrder = sortOrder,
    )

    @Test
    fun cacheMatchesProfile_whenTeamRoomsMissingForMember() {
        val rooms = listOf(
            room("global", ChatAllianceIds.GLOBAL, "Межсерв", 0),
            room("srv", "srv:1", "#1", 0),
        )
        assertFalse(ChatTeamRoomsMembership.cacheMatchesProfile(rooms, "team123"))
    }

    @Test
    fun cacheMatchesProfile_whenTeamRoomsPresentForMember() {
        val rooms = listOf(
            room("global", ChatAllianceIds.GLOBAL, "Межсерв", 0),
            room("hub", "pt:team123", "Альянс", 1),
            room("raid", "pt:team123", "Рейд", 2),
        )
        assertTrue(ChatTeamRoomsMembership.cacheMatchesProfile(rooms, "team123"))
    }

    @Test
    fun cacheMatchesProfile_whenNoTeamAndNoTeamRooms() {
        val rooms = listOf(
            room("global", ChatAllianceIds.GLOBAL, "Межсерв", 0),
        )
        assertTrue(ChatTeamRoomsMembership.cacheMatchesProfile(rooms, null))
    }

    @Test
    fun cacheMatchesProfile_whenNoTeamButStaleTeamRooms() {
        val rooms = listOf(
            room("hub", "pt:team123", "Альянс", 1),
            room("raid", "pt:team123", "Рейд", 2),
        )
        assertFalse(ChatTeamRoomsMembership.cacheMatchesProfile(rooms, null))
    }
}
