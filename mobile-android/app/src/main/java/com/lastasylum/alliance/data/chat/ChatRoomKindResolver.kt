package com.lastasylum.alliance.data.chat

/**
 * Single source of truth for alliance chat room kinds (hub «Альянс», raid «Рейд», etc.).
 * Used by app UI, overlay routing, and room prefs sync.
 */
enum class ChatRoomKind {
    GlobalUnion,
    Server,
    AllianceHub,
    Raid,
    Other,
}

object ChatRoomKindResolver {
    fun kindOf(room: ChatRoomDto): ChatRoomKind = when {
        room.allianceId == ChatAllianceIds.GLOBAL -> ChatRoomKind.GlobalUnion
        ChatAllianceIds.isServerScope(room.allianceId) -> ChatRoomKind.Server
        isAllianceHubRoom(room) -> ChatRoomKind.AllianceHub
        isAllianceRaidRoom(room) -> ChatRoomKind.Raid
        else -> ChatRoomKind.Other
    }

    fun isAllianceHubRoom(room: ChatRoomDto): Boolean {
        if (room.allianceId.isNullOrBlank() || !room.allianceId.startsWith("pt:")) return false
        if (room.allianceId == ChatAllianceIds.GLOBAL) return false
        if (ChatAllianceIds.isServerScope(room.allianceId)) return false
        return room.sortOrder == 1
    }

    fun isAllianceRaidRoom(room: ChatRoomDto): Boolean {
        if (room.allianceId.isNullOrBlank() || !room.allianceId.startsWith("pt:")) {
            return false
        }
        return room.sortOrder == 2 ||
            room.title.trim().equals(ChatRaidRoomSync.RAID_ROOM_TITLE, ignoreCase = true)
    }

    fun allianceHubRoom(rooms: List<ChatRoomDto>): ChatRoomDto? =
        rooms.firstOrNull { isAllianceHubRoom(it) }

    fun allianceRaidRoom(rooms: List<ChatRoomDto>): ChatRoomDto? =
        rooms.firstOrNull { isAllianceRaidRoom(it) }
}
