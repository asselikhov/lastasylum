package com.lastasylum.alliance.data.chat.outbox

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.di.ChatViewModelRegistry

/**
 * Updates in-memory chat UI after [OutboxSendWorker] / background outbox resume succeeds
 * without a bound composable [ChatViewModel] scope.
 */
object OutboxSendUiBridge {
    fun onSendSuccess(entry: OutboxEntry, sent: ChatMessage) {
        val roomId = entry.roomId.trim()
        if (roomId.isNotEmpty()) {
            ChatSessionCache.getFreshMessages(roomId)?.let { cached ->
                ChatSessionCache.updateMessages(
                    roomId,
                    mergeSentIntoCachedMessages(
                        cached = cached,
                        sent = sent,
                        pendingMessageId = entry.pendingMessageId,
                    ),
                )
            }
        }
        ChatViewModelRegistry.onOutboxSendSuccess(
            clientMessageId = entry.clientMessageId,
            sent = sent,
            pendingIdHint = entry.pendingMessageId,
        )
    }

    private fun mergeSentIntoCachedMessages(
        cached: List<ChatMessage>,
        sent: ChatMessage,
        pendingMessageId: String,
    ): List<ChatMessage> {
        val serverId = sent._id?.trim().orEmpty()
        val pending = pendingMessageId.trim()
        var list = if (pending.isEmpty()) {
            cached
        } else {
            cached.filter { it._id?.trim() != pending }
        }
        if (serverId.isEmpty()) {
            return if (list.any { it._id?.trim() == sent._id?.trim() }) list else list + sent
        }
        if (list.any { it._id?.trim() == serverId }) {
            return list.map { if (it._id?.trim() == serverId) sent else it }
        }
        return list + sent
    }
}
