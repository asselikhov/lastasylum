package com.lastasylum.alliance.data.chat.sync

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.store.MessageStore
import com.lastasylum.alliance.data.chat.outbox.ChatOutbox
import com.lastasylum.alliance.data.chat.outbox.OutboxEntry
import com.lastasylum.alliance.data.chat.outbox.OutboxSendSource
import com.lastasylum.alliance.data.telemetry.DeliveryLatencyTracker
import com.lastasylum.alliance.data.telemetry.LatencySpanType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class ChatSyncPhase {
    Idle,
    Importing,
    Ready,
    Syncing,
}

data class ChatSyncState(
    val phase: ChatSyncPhase = ChatSyncPhase.Idle,
    val boundUserId: String? = null,
)

/**
 * Orchestrates REST/socket → Room and durable outbox resume.
 */
class ChatSyncEngine(
    private val messageStore: MessageStore,
    private val chatOutbox: ChatOutbox,
    private val repository: ChatSyncRestGateway,
    private val latencyTracker: DeliveryLatencyTracker,
) {
    private val _state = MutableStateFlow(ChatSyncState())
    val state: StateFlow<ChatSyncState> = _state.asStateFlow()

    suspend fun bindUser(userId: String) {
        val uid = userId.trim()
        if (uid.isEmpty()) return
        _state.value = ChatSyncState(phase = ChatSyncPhase.Ready, boundUserId = uid)
    }

    suspend fun dualWriteSnapshot(
        userId: String,
        rooms: List<ChatRoomDto>,
        messagesByRoom: Map<String, Pair<List<ChatMessage>, Boolean>>,
    ) {
        val uid = userId.trim()
        if (uid.isEmpty()) return
        withContext(Dispatchers.IO) {
            if (rooms.isNotEmpty()) {
                messageStore.upsertRooms(uid, rooms)
            }
            messagesByRoom.forEach { (roomId, pair) ->
                val (messages, hasMoreOlder) = pair
                if (messages.isNotEmpty()) {
                    messageStore.upsertMessages(uid, roomId, messages, hasMoreOlder)
                }
            }
        }
    }

    suspend fun markRoomRead(userId: String, roomId: String, messageId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            messageStore.setReadCursor(userId, roomId, messageId)
            repository.markRoomRead(roomId, messageId).map { }
        }

    suspend fun onSocketMessageConfirmed(
        userId: String,
        roomId: String,
        message: ChatMessage,
        clientMessageId: String? = null,
    ) {
        messageStore.upsertMessages(userId, roomId, listOf(message))
        clientMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let { cid ->
            latencyTracker.endSpanByCorrelation(LatencySpanType.ChatSendToSocket, cid, "ok")
        }
    }

    suspend fun resumePendingOutbox(scope: CoroutineScope, userId: String) {
        chatOutbox.resumePending(scope, userId) { entry ->
            sendOutboxEntry(entry)
        }
    }

    suspend fun resumePendingOutboxSync(userId: String) {
        val uid = userId.trim()
        if (uid.isEmpty()) return
        bindUser(uid)
        chatOutbox.resumePendingSync(uid) { entry ->
            sendOutboxEntry(entry)
        }
    }

    /** After socket reconnect: retry durable outbox sends for the bound user. */
    suspend fun reconnectOutboxResume(scope: CoroutineScope) {
        val uid = _state.value.boundUserId?.trim().orEmpty()
        if (uid.isEmpty()) return
        resumePendingOutbox(scope, uid)
    }

    suspend fun sendOutboxEntry(
        entry: OutboxEntry,
        skipSocket: Boolean = false,
    ): Result<ChatMessage> {
        if (!skipSocket) {
            latencyTracker.startSpan(LatencySpanType.ChatSendToSocket, entry.clientMessageId)
        }
        val result = when (entry.source) {
            OutboxSendSource.OverlayRaid ->
                repository.sendOverlayRaidCommandFast(
                    text = entry.text,
                    roomId = entry.roomId,
                    gameEventAlert = null,
                    clientMessageId = entry.clientMessageId,
                    maxAttempts = 1,
                )
            OutboxSendSource.ChatUi ->
                repository.sendMessageWithRetriesForChatUi(
                    text = entry.text,
                    roomId = entry.roomId,
                    replyToMessageId = entry.replyToMessageId,
                    attachments = entry.attachments,
                    excavationAlert = entry.excavationAlert,
                    clientMessageId = entry.clientMessageId,
                    skipSocket = skipSocket,
                )
        }
        result.onSuccess { sent ->
            chatOutbox.confirmSend(
                userId = _state.value.boundUserId.orEmpty(),
                clientMessageId = entry.clientMessageId,
                serverMessage = sent,
            )
        }.onFailure { err ->
            chatOutbox.markFailed(entry.clientMessageId, err.message ?: "send_failed")
        }
        return result
    }

    suspend fun sendEnqueuedOutbox(
        clientMessageId: String,
        skipSocket: Boolean = false,
    ): Result<ChatMessage> {
        val entry = chatOutbox.tryClaimForSend(clientMessageId)
            ?: return Result.failure(IllegalStateException("outbox_claim_failed"))
        return sendOutboxEntry(entry, skipSocket = skipSocket)
    }

    data class StoredRoomSnapshot(
        val messages: List<ChatMessage>,
        val hasMoreOlder: Boolean,
    )

    suspend fun loadRoomSnapshotFromStore(userId: String, roomId: String): StoredRoomSnapshot? {
        val uid = userId.trim()
        val rid = roomId.trim()
        if (uid.isEmpty() || rid.isEmpty()) return null
        val messages = messageStore.getMessages(uid, rid)
        val hasMoreOlder = messageStore.getHasMoreOlderHint(uid, rid)
        if (messages.isNotEmpty()) {
            return StoredRoomSnapshot(messages = messages, hasMoreOlder = hasMoreOlder)
        }
        if (messageStore.isRoomKnown(uid, rid)) {
            return StoredRoomSnapshot(messages = emptyList(), hasMoreOlder = hasMoreOlder)
        }
        return null
    }

    suspend fun bootstrapLoadRooms(userId: String): Result<List<ChatRoomDto>> {
        val uid = userId.trim()
        if (uid.isEmpty()) return Result.failure(IllegalArgumentException("user_required"))
        return repository.listRooms().onSuccess { rooms ->
            if (rooms.isNotEmpty()) {
                messageStore.upsertRooms(uid, rooms)
            }
        }
    }

    suspend fun loadRoomFromStore(userId: String, roomId: String): List<ChatMessage>? =
        loadRoomSnapshotFromStore(userId, roomId)?.messages

    suspend fun loadRoomsFromStore(userId: String): List<ChatRoomDto>? {
        val rooms = messageStore.getRooms(userId)
        return rooms.takeIf { it.isNotEmpty() }
    }

    suspend fun mirrorIncomingMessages(
        userId: String,
        roomId: String,
        messages: List<ChatMessage>,
        hasMoreOlder: Boolean = false,
    ) {
        val uid = userId.trim()
        val rid = roomId.trim()
        if (uid.isEmpty() || rid.isEmpty() || messages.isEmpty()) return
        withContext(Dispatchers.IO) {
            messageStore.upsertMessages(uid, rid, messages, hasMoreOlder)
        }
    }

    suspend fun onIncomingSocketMessage(
        userId: String,
        message: ChatMessage,
    ) {
        val roomId = message.roomId.trim()
        if (roomId.isEmpty()) return
        mirrorIncomingMessages(userId, roomId, listOf(message))
    }
}
