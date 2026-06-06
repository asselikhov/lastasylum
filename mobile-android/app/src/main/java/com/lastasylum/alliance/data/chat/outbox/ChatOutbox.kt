package com.lastasylum.alliance.data.chat.outbox

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.store.ChatOutboxEntity
import com.lastasylum.alliance.data.chat.store.ChatStoreJson
import com.lastasylum.alliance.data.chat.store.MessageStore
import com.lastasylum.alliance.data.chat.store.SquadRelayDatabase
import com.lastasylum.alliance.data.telemetry.DeliveryLatencyTracker
import com.lastasylum.alliance.data.telemetry.LatencySpanType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatOutbox(
    private val db: SquadRelayDatabase,
    private val messageStore: MessageStore,
    private val latencyTracker: DeliveryLatencyTracker,
) {
    private val dao get() = db.chatOutboxDao()
    private val resumeMutex = Mutex()

    fun observeActive(userId: String): Flow<List<OutboxEntry>> =
        dao.observeActive(userId).map { rows -> rows.map(::toEntry) }

    fun observeActiveForRoom(userId: String, roomId: String): Flow<List<OutboxEntry>> =
        dao.observeActiveForRoom(userId, roomId.trim()).map { rows -> rows.map(::toEntry) }

    suspend fun enqueueSend(
        userId: String,
        roomId: String,
        text: String,
        replyToMessageId: String?,
        attachments: List<String>?,
        excavationAlert: Boolean,
        source: OutboxSendSource,
        currentUserId: String,
        currentUserRole: String,
        senderUsername: String,
        pendingMessageId: String = "pending-${UUID.randomUUID()}",
    ): OutboxEnqueueResult = withContext(Dispatchers.IO) {
        val clientMessageId = UUID.randomUUID().toString()
        val optimistic = ChatMessage(
            _id = pendingMessageId,
            allianceId = "",
            roomId = roomId.trim(),
            senderId = currentUserId,
            senderUsername = senderUsername,
            senderRole = currentUserRole,
            text = text.trim(),
            replyToMessageId = replyToMessageId?.trim()?.takeIf { it.isNotEmpty() },
            attachments = emptyList(),
            createdAt = java.time.Instant.now().toString(),
        )
        messageStore.upsertMessages(userId, roomId, listOf(optimistic))
        dao.upsert(
            ChatOutboxEntity(
                clientMessageId = clientMessageId,
                userId = userId,
                roomId = roomId.trim(),
                pendingMessageId = pendingMessageId,
                text = text.trim(),
                replyToMessageId = replyToMessageId?.trim()?.takeIf { it.isNotEmpty() },
                attachmentsJson = ChatStoreJson.attachmentsToJson(attachments),
                excavationAlert = excavationAlert,
                source = source.wire,
                state = OutboxSendState.Pending.wire,
                attempts = 0,
                createdAtMs = System.currentTimeMillis(),
            ),
        )
        val spanId = latencyTracker.startSpan(LatencySpanType.ChatSendToHttpAck, clientMessageId)
        OutboxEnqueueResult(
            clientMessageId = clientMessageId,
            pendingMessageId = pendingMessageId,
            optimisticMessage = optimistic,
            httpAckSpanId = spanId,
        )
    }

    suspend fun markSending(clientMessageId: String) = withContext(Dispatchers.IO) {
        val row = dao.getByClientId(clientMessageId) ?: return@withContext
        dao.upsert(row.copy(state = OutboxSendState.Sending.wire))
    }

    suspend fun confirmSend(
        userId: String,
        clientMessageId: String,
        serverMessage: ChatMessage,
        httpAckSpanId: Long? = null,
    ) = withContext(Dispatchers.IO) {
        messageStore.upsertMessages(userId, serverMessage.roomId, listOf(serverMessage))
        dao.getByClientId(clientMessageId)?.let { row ->
            dao.upsert(row.copy(state = OutboxSendState.Sent.wire))
            dao.delete(clientMessageId)
        }
        httpAckSpanId?.let { latencyTracker.endSpan(it, "ok") }
        latencyTracker.endSpanByCorrelation(LatencySpanType.ChatSendToSocket, clientMessageId, "ok")
        dao.pruneSent(userId)
    }

    suspend fun markFailed(clientMessageId: String, error: String, httpAckSpanId: Long? = null) =
        withContext(Dispatchers.IO) {
            val row = dao.getByClientId(clientMessageId) ?: return@withContext
            dao.upsert(
                row.copy(
                    state = OutboxSendState.Failed.wire,
                    attempts = row.attempts + 1,
                    lastError = error.take(512),
                ),
            )
            httpAckSpanId?.let { latencyTracker.endSpan(it, "error") }
        }

    suspend fun hasActiveSend(clientMessageId: String): Boolean =
        withContext(Dispatchers.IO) {
            dao.getByClientId(clientMessageId)?.state?.let {
                OutboxSendState.fromWire(it) != OutboxSendState.Sent
            } == true
        }

    suspend fun getByClientId(clientMessageId: String): OutboxEntry? =
        withContext(Dispatchers.IO) {
            dao.getByClientId(clientMessageId)?.let(::toEntry)
        }

    suspend fun resumePending(
        scope: CoroutineScope,
        userId: String,
        sendBlock: suspend (OutboxEntry) -> Result<ChatMessage>,
    ) = resumeMutex.withLock {
        val pending = withContext(Dispatchers.IO) { dao.getResumable(userId) }
        pending.forEach { row ->
            val entry = toEntry(row)
            scope.launch(Dispatchers.IO) {
                markSending(entry.clientMessageId)
                sendBlock(entry)
            }
        }
    }

    private fun toEntry(row: ChatOutboxEntity): OutboxEntry = OutboxEntry(
        clientMessageId = row.clientMessageId,
        pendingMessageId = row.pendingMessageId,
        roomId = row.roomId,
        text = row.text,
        replyToMessageId = row.replyToMessageId,
        attachments = ChatStoreJson.attachmentsFromJson(row.attachmentsJson),
        excavationAlert = row.excavationAlert,
        source = OutboxSendSource.entries.firstOrNull { it.wire == row.source } ?: OutboxSendSource.ChatUi,
        state = OutboxSendState.fromWire(row.state),
        attempts = row.attempts,
        createdAtMs = row.createdAtMs,
        lastError = row.lastError,
    )
}

data class OutboxEnqueueResult(
    val clientMessageId: String,
    val pendingMessageId: String,
    val optimisticMessage: ChatMessage,
    val httpAckSpanId: Long,
)
