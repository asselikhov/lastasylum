package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage

/** Raid strip vs hub-only socket routing for overlay chat. */
internal object OverlayStripMessageRouter {
    fun isOverlayRaidRoomMessage(msg: ChatMessage, raidRoomId: String?): Boolean {
        val raidId = raidRoomId?.trim().orEmpty()
        if (raidId.isEmpty()) return false
        val room = msg.roomId.trim()
        return room.isEmpty() || room == raidId
    }

    fun shouldRouteHubUnread(msg: ChatMessage, hubRoomId: String?, raidRoomId: String?): Boolean {
        val hubId = hubRoomId?.trim().orEmpty()
        if (hubId.isEmpty()) return false
        if (msg.roomId.trim() != hubId) return false
        return !isOverlayRaidRoomMessage(msg, raidRoomId)
    }
}
