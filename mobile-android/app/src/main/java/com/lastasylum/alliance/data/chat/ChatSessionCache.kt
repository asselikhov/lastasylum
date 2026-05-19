package com.lastasylum.alliance.data.chat

/**
 * Short-lived cache so overlay chat can open without re-fetching [ChatRepository.listRooms].
 */
object ChatSessionCache {
    private const val TTL_MS = 30_000L

    @Volatile
    private var cachedRooms: List<ChatRoomDto>? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    fun getFreshRooms(): List<ChatRoomDto>? {
        val rooms = cachedRooms ?: return null
        if (System.currentTimeMillis() - cachedAtMs > TTL_MS) return null
        return rooms
    }

    fun update(rooms: List<ChatRoomDto>) {
        cachedRooms = rooms
        cachedAtMs = System.currentTimeMillis()
    }

    fun clear() {
        cachedRooms = null
        cachedAtMs = 0L
    }
}
