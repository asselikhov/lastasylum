package com.lastasylum.alliance.data.chat

/**
 * Keeps overlay raid voice/chat in sync with the player's team raid room from [listRooms].
 */
object ChatRaidRoomSync {
    fun isAllianceRaidRoom(room: ChatRoomDto): Boolean =
        room.sortOrder == 2 &&
            !room.allianceId.isNullOrBlank() &&
            room.allianceId.startsWith("pt:")

    fun applyRaidRoomPreference(rooms: List<ChatRoomDto>, preferences: ChatRoomPreferences) {
        val raid = rooms.firstOrNull { isAllianceRaidRoom(it) }
        if (raid != null) {
            preferences.setRaidRoomId(raid.id)
        } else {
            preferences.clearRaidRoomId()
        }
    }
}
