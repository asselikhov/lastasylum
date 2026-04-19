package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage

/** Newest-first in-memory cap to keep scroll/diff bounded for very long threads. */
internal const val CHAT_MAX_MESSAGES_IN_MEMORY = 800

internal data class MessageUpsertResult(
    val messages: List<ChatMessage>,
    val newestMessageKey: String?,
)

internal fun fallbackMessageKey(message: ChatMessage): String {
    return message._id
        ?: "${message.senderId}_${message.createdAt}_${message.text.hashCode()}"
}

/** Keeps the first [max] messages (newest at index 0); drops oldest tail. */
internal fun capNewestFirst(messages: List<ChatMessage>, max: Int): List<ChatMessage> {
    if (messages.size <= max) return messages
    return messages.take(max)
}

internal fun upsertMessage(
    current: List<ChatMessage>,
    incoming: ChatMessage,
    knownMessageIds: MutableSet<String>,
): MessageUpsertResult {
    val id = incoming._id
    if (id != null) {
        val existingIndex = current.indexOfFirst { it._id == id }
        if (existingIndex >= 0) {
            val updated = current.toMutableList()
            updated[existingIndex] = incoming
            return MessageUpsertResult(
                messages = updated,
                newestMessageKey = null,
            )
        }
        knownMessageIds.add(id)
        return MessageUpsertResult(
            messages = listOf(incoming) + current,
            newestMessageKey = id,
        )
    }
    val exists = current.any {
        it._id == null &&
            it.senderId == incoming.senderId &&
            it.createdAt == incoming.createdAt &&
            it.text == incoming.text
    }
    if (exists) {
        return MessageUpsertResult(current, null)
    }
    return MessageUpsertResult(
        messages = listOf(incoming) + current,
        newestMessageKey = fallbackMessageKey(incoming),
    )
}

internal fun mergeOlderPage(
    current: List<ChatMessage>,
    olderPage: List<ChatMessage>,
    knownMessageIds: MutableSet<String>,
): List<ChatMessage> {
    val appended = olderPage.filter { msg ->
        val mid = msg._id
        mid == null || knownMessageIds.add(mid)
    }
    return capNewestFirst(current + appended, CHAT_MAX_MESSAGES_IN_MEMORY)
}

internal fun scrubMessagesAfterRemove(
    messages: List<ChatMessage>,
    removedId: String,
    knownMessageIds: MutableSet<String>,
): List<ChatMessage> {
    knownMessageIds.remove(removedId)
    return messages
        .filterNot { it._id == removedId }
        .map { message ->
            if (message.replyTo?._id == removedId) {
                message.copy(replyTo = null)
            } else {
                message
            }
        }
}

internal fun syncSelections(state: ChatState): ChatState {
    if (state.replyToMessage == null &&
        state.activeActionMessageId == null &&
        state.confirmDeleteMessageId == null &&
        state.selectedMessageIds.isEmpty() &&
        !state.confirmBulkDelete
    ) {
        return state
    }
    val messages = state.messages
    val byId = HashMap<String, ChatMessage>(messages.size.coerceAtLeast(16))
    for (m in messages) {
        m._id?.let { byId[it] = m }
    }
    val replyId = state.replyToMessage?._id
    val syncedReply = replyId?.let { byId[it] }?.takeIf { it.deletedAt == null }
    val activeActionExists = state.activeActionMessageId?.let { byId.containsKey(it) } == true
    val deleteTargetExists = state.confirmDeleteMessageId?.let { byId.containsKey(it) } == true
    val syncedSelection = state.selectedMessageIds.filter { id ->
        byId[id]?.let { m ->
            m.deletedAt == null && canDeleteChatMessage(m, state.currentUserId, state.currentUserRole)
        } == true
    }.toSet()
    val keepBulkConfirm = state.confirmBulkDelete && syncedSelection.isNotEmpty()
    return state.copy(
        replyToMessage = syncedReply,
        activeActionMessageId = if (activeActionExists) state.activeActionMessageId else null,
        confirmDeleteMessageId = if (deleteTargetExists) {
            state.confirmDeleteMessageId
        } else {
            null
        },
        selectedMessageIds = syncedSelection,
        confirmBulkDelete = keepBulkConfirm,
    )
}

internal fun canDeleteChatMessage(
    message: ChatMessage,
    currentUserId: String,
    currentUserRole: String,
): Boolean {
    if (message._id.isNullOrBlank()) return false
    return message.senderId == currentUserId || currentUserRole == "R5"
}
