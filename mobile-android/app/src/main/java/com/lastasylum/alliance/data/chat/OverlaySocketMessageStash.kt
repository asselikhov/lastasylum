package com.lastasylum.alliance.data.chat

/**
 * VM-independent stash for overlay socket traffic while the chat panel is closed
 * or before [com.lastasylum.alliance.ui.chat.ChatViewModel] is resolved.
 */
object OverlaySocketMessageStash {
    private const val MAX_MESSAGES_PER_ROOM = 200

    fun stash(message: ChatMessage) {
        if (message.isCompactReactionSocketUpdate()) return
        val roomId = message.roomId.trim()
        val messageId = message._id?.trim().orEmpty()
        if (roomId.isEmpty() || messageId.isEmpty()) return
        val existing = ChatSessionCache.getFreshMessages(roomId) ?: emptyList()
        val existingIndex = existing.indexOfFirst { it._id?.trim() == messageId }
        val next = when {
            existingIndex >= 0 -> {
                existing.toMutableList().also { list ->
                    list[existingIndex] = message.mergeIncomingChatUpdate(existing[existingIndex])
                }
            }
            else -> listOf(message) + existing
        }
        ChatSessionCache.updateMessages(
            roomId,
            next.take(MAX_MESSAGES_PER_ROOM),
        )
    }
}
