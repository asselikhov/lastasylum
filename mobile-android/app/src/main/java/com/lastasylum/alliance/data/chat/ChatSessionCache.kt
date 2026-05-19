package com.lastasylum.alliance.data.chat

/**
 * Short-lived cache so overlay chat and HUD can avoid duplicate network round-trips.
 */
object ChatSessionCache {
    private const val ROOMS_TTL_MS = 30_000L
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

    fun update(rooms: List<ChatRoomDto>) {
        cachedRooms = rooms
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
