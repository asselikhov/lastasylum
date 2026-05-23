package com.lastasylum.alliance.data.chat

/**
 * Short-lived cache so overlay chat and HUD can avoid duplicate network round-trips.
 */
object ChatSessionCache {
    private const val ROOMS_TTL_MS = 90_000L
    private const val MESSAGES_TTL_MS = 45_000L

    @Volatile
    private var cachedRooms: List<ChatRoomDto>? = null

    @Volatile
    private var cachedRoomsAtMs: Long = 0L

    @Volatile
    private var cachedMessagesRoomId: String? = null

    @Volatile
    private var cachedMessages: List<ChatMessage>? = null

    @Volatile
    private var cachedMessagesAtMs: Long = 0L

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

    fun getFreshMessages(roomId: String): List<ChatMessage>? {
        if (roomId.isBlank()) return null
        val messages = cachedMessages ?: return null
        if (cachedMessagesRoomId != roomId) return null
        if (System.currentTimeMillis() - cachedMessagesAtMs > MESSAGES_TTL_MS) return null
        return messages
    }

    fun updateMessages(roomId: String, messages: List<ChatMessage>) {
        if (roomId.isBlank()) return
        cachedMessagesRoomId = roomId
        cachedMessages = messages
        cachedMessagesAtMs = System.currentTimeMillis()
    }

    fun clear() {
        cachedRooms = null
        cachedRoomsAtMs = 0L
        cachedMessages = null
        cachedMessagesRoomId = null
        cachedMessagesAtMs = 0L
    }
}
