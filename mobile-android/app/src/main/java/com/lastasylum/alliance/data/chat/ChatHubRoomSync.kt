package com.lastasylum.alliance.data.chat

/**
 * Keeps overlay hub (alliance main chat) room id in sync with [listRooms] for socket + HUD badges.
 */
object ChatHubRoomSync {
    fun isAllianceHubRoom(room: ChatRoomDto): Boolean {
        if (room.allianceId.isNullOrBlank() || !room.allianceId.startsWith("pt:")) {
            return false
        }
        if (room.allianceId == ChatAllianceIds.GLOBAL) return false
        if (ChatAllianceIds.isServerScope(room.allianceId)) return false
        return room.sortOrder == 1
    }

    fun allianceHubRoom(rooms: List<ChatRoomDto>): ChatRoomDto? =
        rooms.firstOrNull { isAllianceHubRoom(it) }

    fun applyHubRoomPreference(rooms: List<ChatRoomDto>, preferences: ChatRoomPreferences) {
        val hub = allianceHubRoom(rooms)
        if (hub != null) {
            preferences.setHubRoomId(hub.id)
        } else {
            preferences.clearHubRoomId()
        }
    }
}
