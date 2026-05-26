package com.lastasylum.alliance.data.chat

/**
 * Short-lived cache so overlay chat and HUD can avoid duplicate network round-trips.
 */
object ChatSessionCache {
    private const val ROOMS_TTL_MS = 45_000L
    private const val MESSAGES_TTL_MS = 45_000L
    private const val MAX_CACHED_MESSAGE_ROOMS = 8

    @Volatile
    private var cachedRooms: List<ChatRoomDto>? = null

    @Volatile
    private var cachedRoomsAtMs: Long = 0L

    private data class RoomMessagesEntry(
        val messages: List<ChatMessage>,
        val atMs: Long,
    )

    /** LRU: most recently touched room at end. */
    private val messagesByRoom = LinkedHashMap<String, RoomMessagesEntry>(MAX_CACHED_MESSAGE_ROOMS + 1, 0.75f, true)

    fun getFreshRooms(): List<ChatRoomDto>? {
        val rooms = cachedRooms ?: return null
        if (System.currentTimeMillis() - cachedRoomsAtMs > ROOMS_TTL_MS) return null
        return rooms
    }

    /** Force next bootstrap/HUD path to refetch listRooms. */
    fun invalidateRooms() {
        cachedRoomsAtMs = 0L
    }

    fun update(rooms: List<ChatRoomDto>) {
        cachedRooms = rooms
        cachedRoomsAtMs = System.currentTimeMillis()
    }

    /** Keep listRooms cache aligned after mark-read so overlay/bootstrap do not resurrect badges. */
    fun patchRoomRead(roomId: String, lastReadMessageId: String) {
        if (roomId.isBlank() || lastReadMessageId.isBlank()) return
        val current = cachedRooms ?: return
        cachedRooms = current.map { room ->
            if (room.id != roomId) room
            else room.copy(unreadCount = 0, lastReadMessageId = lastReadMessageId)
        }
        cachedRoomsAtMs = System.currentTimeMillis()
    }

    fun patchRoomUnread(
        roomId: String,
        unreadCount: Int,
        lastReadMessageId: String? = null,
    ) {
        if (roomId.isBlank()) return
        val current = cachedRooms ?: return
        cachedRooms = current.map { room ->
            if (room.id != roomId) room
            else room.copy(
                unreadCount = unreadCount.coerceAtLeast(0),
                lastReadMessageId = lastReadMessageId?.takeIf { it.isNotBlank() }
                    ?: room.lastReadMessageId,
            )
        }
        cachedRoomsAtMs = System.currentTimeMillis()
    }

    fun getFreshMessages(roomId: String): List<ChatMessage>? {
        if (roomId.isBlank()) return null
        val entry = messagesByRoom[roomId] ?: return null
        if (System.currentTimeMillis() - entry.atMs > MESSAGES_TTL_MS) {
            messagesByRoom.remove(roomId)
            return null
        }
        return entry.messages
    }

    fun updateMessages(roomId: String, messages: List<ChatMessage>) {
        if (roomId.isBlank()) return
        messagesByRoom[roomId] = RoomMessagesEntry(
            messages = messages,
            atMs = System.currentTimeMillis(),
        )
        while (messagesByRoom.size > MAX_CACHED_MESSAGE_ROOMS) {
            val eldest = messagesByRoom.keys.firstOrNull() ?: break
            messagesByRoom.remove(eldest)
        }
    }

    fun clear() {
        cachedRooms = null
        cachedRoomsAtMs = 0L
        messagesByRoom.clear()
    }
}
