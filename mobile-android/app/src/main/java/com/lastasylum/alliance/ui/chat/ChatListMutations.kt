package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.mergePreservingAttachments

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

/** Drop optimistic rows when the server message for the same outgoing text arrives. */
internal fun dropMatchingPendingOutgoing(
    current: List<ChatMessage>,
    incoming: List<ChatMessage>,
    currentUserId: String,
): List<ChatMessage> {
    val selfId = currentUserId.trim()
    if (selfId.isEmpty() || incoming.isEmpty()) return current
    val confirmed = incoming.filter { msg ->
        val id = msg._id?.trim().orEmpty()
        msg.senderId.trim() == selfId &&
            id.isNotEmpty() &&
            !id.startsWith("pending-")
    }
    if (confirmed.isEmpty()) return current
    return current.filter { msg ->
        val id = msg._id?.trim().orEmpty()
        if (!id.startsWith("pending-")) return@filter true
        !confirmed.any { sent ->
            sent.text.trim() == msg.text.trim() &&
                sent.replyToMessageId == msg.replyToMessageId
        }
    }
}

internal fun rebuildMessageIdIndex(
    messages: List<ChatMessage>,
    index: MutableMap<String, Int>,
) {
    index.clear()
    messages.forEachIndexed { i, msg ->
        msg._id?.let { index[it] = i }
    }
}

internal fun upsertMessage(
    current: List<ChatMessage>,
    incoming: ChatMessage,
    knownMessageIds: MutableSet<String>,
    idIndex: MutableMap<String, Int>? = null,
    /** When true, [rebuildMessageIdIndex] runs once after [upsertMessagesBatch]. */
    deferIndexShift: Boolean = false,
): MessageUpsertResult {
    val id = incoming._id
    if (id != null) {
        val existingIndex = idIndex?.get(id) ?: current.indexOfFirst { it._id == id }
        if (existingIndex >= 0) {
            val updated = current.toMutableList()
            updated[existingIndex] = incoming.mergePreservingAttachments(current[existingIndex])
            idIndex?.put(id, existingIndex)
            return MessageUpsertResult(
                messages = updated,
                newestMessageKey = null,
            )
        }
        knownMessageIds.add(id)
        val next = listOf(incoming) + current
        idIndex?.let { map ->
            map[id] = 0
            if (!deferIndexShift) {
                map.entries.forEach { (key, pos) ->
                    if (key != id) map[key] = pos + 1
                }
            }
        }
        return MessageUpsertResult(
            messages = next,
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

internal fun chatMessagesListContentEqual(
    a: List<ChatMessage>,
    b: List<ChatMessage>,
): Boolean {
    if (a === b) return true
    if (a.size != b.size) return false
    for (i in a.indices) {
        if (a[i] != b[i]) return false
    }
    return true
}

internal fun upsertMessagesBatch(
    current: List<ChatMessage>,
    incoming: List<ChatMessage>,
    knownMessageIds: MutableSet<String>,
    idIndex: MutableMap<String, Int>,
    maxMessages: Int = CHAT_MAX_MESSAGES_IN_MEMORY,
): MessageUpsertResult {
    var messages = current
    var newestMessageKey: String? = null
    for (message in incoming) {
        val update = upsertMessage(
            current = messages,
            incoming = message,
            knownMessageIds = knownMessageIds,
            idIndex = idIndex,
            deferIndexShift = true,
        )
        messages = update.messages
        if (update.newestMessageKey != null) {
            newestMessageKey = update.newestMessageKey
        }
    }
    val capped = capNewestFirst(messages, maxMessages)
    rebuildMessageIdIndex(capped, idIndex)
    return MessageUpsertResult(
        messages = capped,
        newestMessageKey = newestMessageKey,
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
            m.deletedAt == null && canDeleteChatMessage(
                m,
                state.currentUserId,
                state.isAppAdmin,
                state.playerTeamSquadRole,
            )
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
