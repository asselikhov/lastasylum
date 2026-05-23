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

    /** Hub badge from [listRooms] API rows + local read cursors (overlay bootstrap). */
    fun allianceHubUnread(
        rooms: List<ChatRoomDto>,
        localReadByRoom: Map<String, String> = emptyMap(),
    ): Int = OverlayGameStatusHudRefresh.allianceHubUnread(rooms, localReadByRoom)

    /** Hub badge from merged [ChatViewModel] room list — matches chat room chip. */
    fun allianceHubDisplayUnread(rooms: List<ChatRoomDto>): Int =
        ChatRoomKindResolver.allianceHubRoom(rooms)
            ?.unreadCount
            ?.coerceAtLeast(0)
            ?: 0

    /** Bottom nav «Чат» — sum of displayed room chips (already merged in [ChatViewModel]). */
    fun tabBadgeTotal(rooms: List<ChatRoomDto>): Int =
        rooms.sumOf { it.unreadCount.coerceAtLeast(0) }.coerceIn(0, 99)

    /** Raw server rows + local cursors (tests / one-shot recompute without merge). */
    fun tabBadgeTotalFromServer(
        rooms: List<ChatRoomDto>,
        localReadByRoom: Map<String, String>,
    ): Int = rooms
        .sumOf { effectiveRoomUnread(it, localReadByRoom) }
        .coerceIn(0, 99)
}
