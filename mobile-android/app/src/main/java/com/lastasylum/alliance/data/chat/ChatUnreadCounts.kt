package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.data.effectiveUnreadCount
import com.lastasylum.alliance.overlay.OverlayGameStatusHudRefresh

/** Shared unread badge math for app tabs, overlay HUD, and cache refresh. */
object ChatUnreadCounts {
    fun effectiveRoomUnread(
        room: ChatRoomDto,
        localReadByRoom: Map<String, String>,
    ): Int = effectiveUnreadCount(
        serverUnread = room.unreadCount,
        lastReadMessageId = room.lastReadMessageId,
        localLastReadMessageId = localReadByRoom[room.id],
    ).coerceAtLeast(0)

    fun allianceHubUnread(
        rooms: List<ChatRoomDto>,
        localReadByRoom: Map<String, String> = emptyMap(),
    ): Int = OverlayGameStatusHudRefresh.allianceHubUnread(rooms, localReadByRoom)

    /** Bottom nav «Чат» — sum of all rooms (hub, raid, server, …). */
    fun tabBadgeTotal(
        rooms: List<ChatRoomDto>,
        localReadByRoom: Map<String, String>,
    ): Int = rooms
        .sumOf { effectiveRoomUnread(it, localReadByRoom) }
        .coerceIn(0, 99)
}
