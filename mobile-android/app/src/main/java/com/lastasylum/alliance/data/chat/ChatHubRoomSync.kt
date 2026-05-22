package com.lastasylum.alliance.data.chat

/**
 * Keeps overlay hub (alliance main chat) room id in sync with [listRooms] for socket + HUD badges.
 */
object ChatHubRoomSync {
    fun isAllianceHubRoom(room: ChatRoomDto): Boolean =
        ChatRoomKindResolver.isAllianceHubRoom(room)

    fun allianceHubRoom(rooms: List<ChatRoomDto>): ChatRoomDto? =
        ChatRoomKindResolver.allianceHubRoom(rooms)

    fun applyHubRoomPreference(rooms: List<ChatRoomDto>, preferences: ChatRoomPreferences) {
        val hub = allianceHubRoom(rooms)
        if (hub != null) {
            preferences.setHubRoomId(hub.id)
        } else {
            preferences.clearHubRoomId()
        }
    }
}
