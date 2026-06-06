package com.lastasylum.alliance.data.chat.sync

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.ui.chat.usecase.ChatRoomsUseCase

object ChatRoomsResolver {
    fun allianceHubRoomId(rooms: List<ChatRoomDto>): String? =
        ChatRoomsUseCase.allianceHubRoomId(rooms)

    fun allianceRaidRoomId(
        rooms: List<ChatRoomDto>,
        prefsRaidRoomId: String?,
    ): String? = ChatRoomsUseCase.allianceRaidRoomId(rooms, prefsRaidRoomId)

    fun messagesBelongToRoom(messages: List<ChatMessage>, roomId: String): Boolean {
        if (messages.isEmpty()) return true
        val rid = roomId.trim()
        if (rid.isEmpty()) return true
        return messages.all { it.roomId.trim() == rid }
    }

    fun resolveOverlayPreferredRoomId(
        rooms: List<ChatRoomDto>,
        preferOverlayRaidRoom: Boolean,
        prefsRaidRoomId: String?,
    ): String? {
        val hubId = allianceHubRoomId(rooms)
        val raidId = allianceRaidRoomId(rooms, prefsRaidRoomId)
        return when {
            preferOverlayRaidRoom && raidId != null -> raidId
            hubId != null -> hubId
            else -> rooms.minByOrNull { it.sortOrder }?.id ?: rooms.firstOrNull()?.id
        }
    }

    fun resolveStartupRoomId(
        rooms: List<ChatRoomDto>,
        selectedFromState: String?,
        selectedFromPrefs: String?,
        preferOverlayRaidRoom: Boolean,
        prefsRaidRoomId: String?,
    ): String {
        val fromState = selectedFromState?.trim().orEmpty()
        if (fromState.isNotEmpty() && rooms.any { it.id == fromState }) return fromState
        val fromPrefs = selectedFromPrefs?.trim().orEmpty()
        if (fromPrefs.isNotEmpty() && rooms.any { it.id == fromPrefs }) return fromPrefs
        return resolveOverlayPreferredRoomId(
            rooms = rooms,
            preferOverlayRaidRoom = preferOverlayRaidRoom,
            prefsRaidRoomId = prefsRaidRoomId,
        ) ?: rooms.first().id
    }
}
