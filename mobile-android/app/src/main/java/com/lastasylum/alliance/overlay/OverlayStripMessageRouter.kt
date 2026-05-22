package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage

/** Raid strip vs hub-only socket routing for overlay chat. */
internal object OverlayStripMessageRouter {
    fun isOverlayRaidRoomMessage(
        msg: ChatMessage,
        raidRoomId: String?,
        extraRaidRoomIds: Set<String> = emptySet(),
    ): Boolean {
        val room = msg.roomId.trim()
        val raidId = raidRoomId?.trim().orEmpty()
        if (room.isEmpty()) return raidId.isNotEmpty() || extraRaidRoomIds.isNotEmpty()
        if (raidId.isNotEmpty() && room == raidId) return true
        if (extraRaidRoomIds.isNotEmpty() && room in extraRaidRoomIds) return true
        return false
    }

    fun shouldRouteHubUnread(msg: ChatMessage, hubRoomId: String?, raidRoomId: String?): Boolean {
        val hubId = hubRoomId?.trim().orEmpty()
        if (hubId.isEmpty()) return false
        if (msg.roomId.trim() != hubId) return false
        return !isOverlayRaidRoomMessage(msg, raidRoomId)
    }
}
