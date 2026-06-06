package com.lastasylum.alliance.data.chat.store

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.sync.ChatSyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Subscribes VM to Room flows for rooms, messages, and read cursors. */
class ChatRoomStoreBindings(
    private val messageStore: MessageStore,
    private val syncEngine: ChatSyncEngine,
    private val scope: CoroutineScope,
    private val userId: String,
) {
    private var roomsJob: Job? = null
    private var messagesJob: Job? = null
    private var readCursorJob: Job? = null

    var onRoomsFromStore: ((List<ChatRoomDto>) -> Unit)? = null
    var onMessagesFromStore: ((String, List<ChatMessage>, Boolean) -> Unit)? = null
    var onReadCursorFromStore: ((String, String?) -> Unit)? = null

    fun startRoomsObserver() {
        if (userId.isBlank()) return
        roomsJob?.cancel()
        roomsJob = scope.launch {
            messageStore.observeRooms(userId)
                .map { rooms -> rooms.filter { it.id.isNotBlank() } }
                .distinctUntilChanged()
                .collect { rooms ->
                    if (rooms.isNotEmpty()) onRoomsFromStore?.invoke(rooms)
                }
        }
    }

    fun bindSelectedRoom(roomId: String?) {
        messagesJob?.cancel()
        readCursorJob?.cancel()
        val rid = roomId?.trim().orEmpty()
        if (userId.isBlank() || rid.isEmpty()) return
        messagesJob = scope.launch {
            messageStore.observeMessages(userId, rid)
                .distinctUntilChanged()
                .collect { messages ->
                    if (messages.isEmpty()) return@collect
                    scope.launch {
                        val hasMore = messageStore.getHasMoreOlderHint(userId, rid)
                        onMessagesFromStore?.invoke(rid, messages, hasMore)
                    }
                }
        }
        readCursorJob = scope.launch {
            messageStore.observeReadCursor(userId, rid)
                .distinctUntilChanged()
                .collect { cursor ->
                    onReadCursorFromStore?.invoke(rid, cursor)
                }
        }
    }

    suspend fun loadRoomsSnapshot(): List<ChatRoomDto>? =
        syncEngine.loadRoomsFromStore(userId)

    suspend fun loadRoomSnapshot(roomId: String): ChatSyncEngine.StoredRoomSnapshot? =
        syncEngine.loadRoomSnapshotFromStore(userId, roomId)

    fun stop() {
        roomsJob?.cancel()
        messagesJob?.cancel()
        readCursorJob?.cancel()
    }
}
