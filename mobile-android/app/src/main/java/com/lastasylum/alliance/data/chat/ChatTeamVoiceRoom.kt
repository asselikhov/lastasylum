package com.lastasylum.alliance.data.chat

/**
 * Overlay team voice: clients send [SOCKET_ROOM_ID] (`"team"`); the server maps it to the
 * player team's hub channel. No chat tab or room list required on the device.
 */
object ChatTeamVoiceRoom {
    const val SOCKET_ROOM_ID = "team"

    fun roomFromPrefs(preferences: ChatRoomPreferences): String? =
        preferences.getHubRoomId()?.trim()?.takeIf { it.isNotEmpty() }

    fun syncFromRooms(rooms: List<ChatRoomDto>, preferences: ChatRoomPreferences): String? {
        ChatHubRoomSync.applyHubRoomPreference(rooms, preferences)
        return roomFromPrefs(preferences)
    }
}
