package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatRoomReadEvent
import com.lastasylum.alliance.data.isObjectIdNewer

/**
 * Merges peer [room:read] into per-room cursors for outgoing read receipts (✓✓).
 */
internal object PeerReadCursorLogic {
    /**
     * @return message id to publish on [otherReadUptoMessageId] when the selected room matches.
     */
    fun mergePeerReadEvent(
        otherReadUptoByRoom: MutableMap<String, String>,
        selectedRoomId: String?,
        event: ChatRoomReadEvent,
        currentUserId: String,
    ): String? {
        if (event.userId.isBlank() || event.messageId.isBlank()) return null
        if (event.userId.trim() == currentUserId.trim()) return null
        val eventRoomId = event.roomId.trim()
        if (eventRoomId.isBlank()) return null
        val cur = otherReadUptoByRoom[eventRoomId]
        if (!isObjectIdNewer(event.messageId, cur)) return null
        otherReadUptoByRoom[eventRoomId] = event.messageId
        return if (selectedRoomId?.trim() == eventRoomId) event.messageId else null
    }
}
