package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.ui.chat.ChatDeliveryMetrics

/**
 * Holds raid strip messages that could not be ingested because raid roomId was not resolved yet.
 * Flushed when [CombatOverlayService] learns the raid room id.
 */
internal object OverlayStripPendingIngest {
    private val pendingById = LinkedHashMap<String, ChatMessage>()

    fun enqueue(message: ChatMessage) {
        val id = message._id?.trim().orEmpty()
        val key = id.ifBlank {
            "${message.senderId}|${message.text}|${message.createdAt}"
        }
        pendingById[key] = message
        ChatDeliveryMetrics.logStripPending("enqueue", id.ifBlank { null })
    }

    fun remove(messageId: String) {
        val id = messageId.trim()
        if (id.isEmpty()) return
        pendingById.remove(id)
    }

    fun flush(ingest: (ChatMessage) -> Unit): Int {
        if (pendingById.isEmpty()) return 0
        val batch = pendingById.values.toList()
        pendingById.clear()
        for (msg in batch) {
            ChatDeliveryMetrics.logStripPending("flush", msg._id)
            ingest(msg)
        }
        return batch.size
    }

    fun pendingCount(): Int = pendingById.size
}
