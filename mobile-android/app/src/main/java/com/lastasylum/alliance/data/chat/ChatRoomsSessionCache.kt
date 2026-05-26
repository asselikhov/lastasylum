package com.lastasylum.alliance.data.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single-flight + memory cache for [ChatApi.listRooms].
 * Complements [ChatSessionCache] (longer TTL, shared with overlay).
 */
internal object ChatRoomsSessionCache {
    private val mutex = Mutex()

    @Volatile
    private var inFlight: Deferred<Result<List<ChatRoomDto>>>? = null

    suspend fun listRooms(fetch: suspend () -> List<ChatRoomDto>): Result<List<ChatRoomDto>> {
        ChatSessionCache.getFreshRooms()?.let { return Result.success(it) }
        return mutex.withLock {
            ChatSessionCache.getFreshRooms()?.let { return@withLock Result.success(it) }
            val existing = inFlight
            if (existing != null && !existing.isCompleted) {
                return@withLock existing.await()
            }
            val deferred = CompletableDeferred<Result<List<ChatRoomDto>>>()
            inFlight = deferred
            val result = runCatching { fetch() }
            result.onSuccess { ChatSessionCache.update(it) }
            deferred.complete(result)
            inFlight = null
            result
        }
    }

    fun invalidate() {
        ChatSessionCache.invalidateRooms()
        inFlight = null
    }
}
