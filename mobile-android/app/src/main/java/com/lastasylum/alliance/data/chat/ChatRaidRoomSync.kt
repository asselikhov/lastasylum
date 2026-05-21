package com.lastasylum.alliance.data.chat

/**
 * Keeps overlay raid voice/chat in sync with the player's team raid room from [listRooms].
 */
object ChatRaidRoomSync {
    /** Must match backend [ALLIANCE_RAID_ROOM_TITLE] («Рейд»). */
    const val RAID_ROOM_TITLE = "Рейд"

    fun isAllianceRaidRoom(room: ChatRoomDto): Boolean {
        if (room.allianceId.isNullOrBlank() || !room.allianceId.startsWith("pt:")) {
            return false
        }
        return room.sortOrder == 2 ||
            room.title.trim().equals(RAID_ROOM_TITLE, ignoreCase = true)
    }

    fun applyRaidRoomPreference(rooms: List<ChatRoomDto>, preferences: ChatRoomPreferences) {
        val raid = rooms.firstOrNull { isAllianceRaidRoom(it) }
        if (raid != null) {
            preferences.setRaidRoomId(raid.id)
        } else {
            preferences.clearRaidRoomId()
        }
    }
}
