package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.data.displayedUnreadCount
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

    fun allianceHubRawUnread(rooms: List<ChatRoomDto>): Int =
        OverlayGameStatusHudRefresh.allianceHubRawUnread(rooms)

    /**
     * True when server still reports hub unread but local read cursor suppresses it
     * (user read in app / on device).
     */
    fun isAllianceHubLocallyReadSuppressed(
        rooms: List<ChatRoomDto>,
        localReadByRoom: Map<String, String>,
    ): Boolean {
        val hub = OverlayGameStatusHudRefresh.allianceHubRoom(rooms) ?: return false
        if (hub.unreadCount <= 0) return false
        return allianceHubUnread(rooms, localReadByRoom) <= 0
    }

    /**
     * Overlay mail chip: same effective count as app when [localReadByRoom] is current.
     * When the hub is read locally, never resurrect from optimistic floor or
     * a previously shown badge — required after reading in the main app before entering the game.
     * When the server lags (effective 0, raw 0), [optimisticFloor] is honored via [displayedUnreadCount].
     */
    fun overlayAllianceHubBadge(
        rooms: List<ChatRoomDto>,
        localReadByRoom: Map<String, String>,
        optimisticFloor: Int = 0,
        previouslyDisplayed: Int = 0,
    ): Int {
        if (isAllianceHubLocallyReadSuppressed(rooms, localReadByRoom)) {
            return 0
        }
        val effective = allianceHubUnread(rooms, localReadByRoom)
        val raw = allianceHubRawUnread(rooms)
        return displayedUnreadCount(
            effectiveUnread = effective,
            previouslyDisplayed = previouslyDisplayed,
            rawServerUnread = raw,
            optimisticFloor = optimisticFloor,
        )
    }

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
