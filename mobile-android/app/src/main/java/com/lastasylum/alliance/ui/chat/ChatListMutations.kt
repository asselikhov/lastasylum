package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.isObjectIdNewer
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatMessageVisibilityPolicy
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.mergeIncomingChatUpdate
import com.lastasylum.alliance.data.chat.mergePreservingAttachments

/** Newest-first in-memory cap to keep scroll/diff bounded for very long threads. */
internal const val CHAT_MAX_MESSAGES_IN_MEMORY = 800

/** Rows shown in a room list must match that room (guards stale UI when switching rooms). */
internal fun filterMessagesForRoom(
    messages: List<ChatMessage>,
    roomId: String,
    hiddenBeforeMessageId: String? = null,
): List<ChatMessage> {
    val rid = roomId.trim()
    if (rid.isEmpty()) return messages
    val hidden = hiddenBeforeMessageId?.trim().orEmpty().ifBlank { null }
    return messages
        .filter { it.roomId.trim() == rid }
        .filter { ChatMessageVisibilityPolicy.isMessageVisible(it, hidden) }
}

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

/** Newest-first: keep first row per [_id] (socket echo + HTTP confirm can briefly duplicate). */
internal fun normalizeOutgoingReplyToId(replyToMessageId: String?): String =
    replyToMessageId?.trim().orEmpty()

/** Tracks in-flight sends so socket echo can be ignored before optimistic row is visible. */
internal fun outgoingMessageFingerprint(
    roomId: String,
    text: String,
    replyToMessageId: String? = null,
): String {
    val rid = roomId.trim()
    return "$rid\u0000${text.trim()}\u0000${normalizeOutgoingReplyToId(replyToMessageId)}"
}

internal fun outgoingTextsMatch(a: ChatMessage, b: ChatMessage): Boolean =
    a.text.trim() == b.text.trim() &&
        normalizeOutgoingReplyToId(a.replyToMessageId) ==
        normalizeOutgoingReplyToId(b.replyToMessageId)

/**
 * Last line of defense before UI apply: strip pending+server pairs and rows that raced
 * ahead of [confirmPendingOutgoingMessage] while [activeOutgoingPendingId] is set.
 */
internal fun sanitizeMessagesAfterRealtimeApply(
    messages: List<ChatMessage>,
    currentUserId: String,
    activeOutgoingPendingId: String?,
): List<ChatMessage> {
    var out = messages
    val pendingId = activeOutgoingPendingId?.trim().orEmpty()
    if (pendingId.isNotEmpty()) {
        val pending = out.find { it._id?.trim() == pendingId }
        if (pending != null) {
            val selfId = currentUserId.trim()
            out = out.filterNot { msg ->
                val id = msg._id?.trim().orEmpty()
                id != pendingId &&
                    id.isNotEmpty() &&
                    !id.startsWith("pending-") &&
                    msg.senderId.trim() == selfId &&
                    outgoingTextsMatch(msg, pending)
            }
        }
    }
    out = stripRedundantPendingOutgoing(out, currentUserId)
    return dedupeMessagesByIdNewestFirst(out)
}

/** Same server [message:new] already shown — drop delayed socket fan-out after HTTP confirm. */
internal fun isDuplicateOwnOutgoingDelivery(
    messages: List<ChatMessage>,
    incoming: ChatMessage,
    idIndex: Map<String, Int>,
): Boolean {
    val serverId = incoming._id?.trim().orEmpty()
    if (serverId.isEmpty()) return false
    val idx = idIndex[serverId] ?: return false
    if (idx !in messages.indices) return false
    val row = messages[idx]
    return row._id?.trim() == serverId && outgoingTextsMatch(row, incoming)
}

/** True while optimistic row is still waiting for HTTP confirm (blocks socket duplicate row). */
internal fun hasMatchingPendingOutgoing(
    messages: List<ChatMessage>,
    incoming: ChatMessage,
    currentUserId: String,
): Boolean {
    val selfId = currentUserId.trim()
    if (selfId.isEmpty() || incoming.senderId.trim() != selfId) return false
    return messages.any { msg ->
        val pendingId = msg._id?.trim().orEmpty()
        pendingId.startsWith("pending-") && outgoingTextsMatch(msg, incoming)
    }
}

/** HTTP confirm of an optimistic row — never inherit spurious [editedAt] from socket/REST. */
internal fun mergeOutgoingConfirmation(
    optimistic: ChatMessage,
    confirmed: ChatMessage,
): ChatMessage =
    confirmed.copy(
        editedAt = null,
        createdAt = confirmed.createdAt ?: optimistic.createdAt,
        attachments = if (confirmed.attachments.isNotEmpty()) {
            confirmed.attachments
        } else {
            optimistic.attachments
        },
        replyTo = confirmed.replyTo ?: optimistic.replyTo,
        replyToMessageId = confirmed.replyToMessageId ?: optimistic.replyToMessageId,
    )

/** Removes optimistic rows when any confirmed server row from self matches the same outgoing. */
internal fun stripRedundantPendingOutgoing(
    messages: List<ChatMessage>,
    currentUserId: String,
): List<ChatMessage> {
    val selfId = currentUserId.trim()
    if (selfId.isEmpty() || messages.isEmpty()) return messages
    val confirmedFingerprints = HashSet<String>()
    for (msg in messages) {
        val id = msg._id?.trim().orEmpty()
        if (msg.senderId.trim() != selfId || id.isEmpty() || id.startsWith("pending-")) continue
        confirmedFingerprints.add(
            "${msg.text.trim()}\u0000${normalizeOutgoingReplyToId(msg.replyToMessageId)}",
        )
    }
    if (confirmedFingerprints.isEmpty()) return messages
    return messages.filter { msg ->
        val id = msg._id?.trim().orEmpty()
        if (!id.startsWith("pending-") || msg.senderId.trim() != selfId) return@filter true
        val fp = "${msg.text.trim()}\u0000${normalizeOutgoingReplyToId(msg.replyToMessageId)}"
        fp !in confirmedFingerprints
    }
}

/**
 * HTTP refresh must not drop rows already shown from socket (API can lag behind realtime).
 * Rows missing from the server page are kept only when still newer than the page anchor
 * (socket ahead of REST) or optimistic pending — not when deleted and left in disk cache.
 */
internal fun mergeLoadedPageWithExisting(
    existing: List<ChatMessage>,
    loaded: List<ChatMessage>,
    maxMessages: Int = CHAT_MAX_MESSAGES_IN_MEMORY,
    excludedMessageIds: Set<String> = emptySet(),
    roomId: String? = null,
): List<ChatMessage> {
    val scopedExisting = roomId?.let { filterMessagesForRoom(existing, it) } ?: existing
    if (loaded.isEmpty()) {
        val kept = scopedExisting.filter { msg ->
            val id = msg._id?.trim().orEmpty()
            id.isNotEmpty() && id !in excludedMessageIds
        }
        return capNewestFirst(kept, maxMessages)
    }
    if (scopedExisting.isEmpty()) return capNewestFirst(loaded, maxMessages)
    val loadedIds = loaded.mapNotNull { msg ->
        msg._id?.trim()?.takeIf { it.isNotEmpty() }
    }.toSet()
    val known = loadedIds.toMutableSet()
    val index = mutableMapOf<String, Int>()
    rebuildMessageIdIndex(loaded, index)
    var messages = loaded
    for (msg in scopedExisting) {
        val id = msg._id?.trim().orEmpty()
        if (id.isEmpty() || id in excludedMessageIds || id in loadedIds) continue
        val update = upsertMessage(
            current = messages,
            incoming = msg,
            knownMessageIds = known,
            idIndex = index,
            deferIndexShift = true,
        )
        messages = update.messages
    }
    val sorted = messages.sortedWith(
        compareByDescending<ChatMessage> { it._id?.trim().orEmpty() },
    )
    return dedupeMessagesByIdNewestFirst(capNewestFirst(sorted, maxMessages))
}

/** Merge [roomMessageCache] rows into visible UI after tab/foreground resume. */
internal fun mergeVisibleMessagesWithRoomCache(
    visible: List<ChatMessage>,
    cached: List<ChatMessage>,
    roomId: String,
    maxMessages: Int = CHAT_MAX_MESSAGES_IN_MEMORY,
    excludedMessageIds: Set<String> = emptySet(),
    hiddenBeforeMessageId: String? = null,
): List<ChatMessage> {
    val scopedVisible = filterMessagesForRoom(visible, roomId, hiddenBeforeMessageId)
    val scopedCached = filterMessagesForRoom(cached, roomId, hiddenBeforeMessageId)
    return when {
        scopedVisible.isEmpty() -> capNewestFirst(scopedCached, maxMessages)
        scopedCached.isEmpty() -> capNewestFirst(scopedVisible, maxMessages)
        else -> mergeLoadedPageWithExisting(
            existing = scopedCached,
            loaded = scopedVisible,
            maxMessages = maxMessages,
            excludedMessageIds = excludedMessageIds,
            roomId = roomId,
        )
    }
}

/**
 * Skip REST refresh when session RAM cache matches visible UI — unless room stash or session
 * cache is ahead of what the user sees (peer messages received while inactive).
 */
internal fun shouldSkipBackgroundMessageRefresh(
    visible: List<ChatMessage>,
    sessionCache: List<ChatMessage>?,
    roomCache: List<ChatMessage>?,
    pageSize: Int,
): Boolean {
    if (sessionCache == null) return false
    if (visible.isEmpty()) return false
    if (sessionCache.size < pageSize || visible.size < pageSize) return false
    if (!roomCache.isNullOrEmpty()) {
        val visibleHead = visible.firstOrNull()?._id?.trim().orEmpty()
        val roomHead = roomCache.firstOrNull()?._id?.trim().orEmpty()
        when {
            roomHead.isNotEmpty() && visibleHead.isNotEmpty() &&
                isObjectIdNewer(roomHead, visibleHead) -> return false
            roomHead == visibleHead && roomCache.size > visible.size -> return false
            roomHead.isNotEmpty() && visibleHead.isEmpty() -> return false
        }
    }
    if (roomCache != null) {
        val sessionHead = sessionCache.firstOrNull()?._id?.trim().orEmpty()
        val roomHead = roomCache.firstOrNull()?._id?.trim().orEmpty()
        if (sessionHead != roomHead || sessionCache.size != roomCache.size) {
            if (roomCache.size > visible.size || sessionCache.size > visible.size) return false
        }
    }
    val head = visible.firstOrNull()?._id?.trim().orEmpty()
    val cachedHead = sessionCache.firstOrNull()?._id?.trim().orEmpty()
    if (head.isEmpty() || cachedHead.isEmpty() || head != cachedHead) return false
    if (sessionCache.size < visible.size) return false
    val compareCount = minOf(visible.size, sessionCache.size, pageSize)
    val visibleIds = visible.take(compareCount).mapNotNull { it._id?.trim()?.takeIf { id -> id.isNotEmpty() } }.toSet()
    val sessionIds = sessionCache.take(compareCount).mapNotNull { it._id?.trim()?.takeIf { id -> id.isNotEmpty() } }.toSet()
    if (visibleIds != sessionIds) return false
    return true
}

internal fun dedupeMessagesByIdNewestFirst(messages: List<ChatMessage>): List<ChatMessage> {
    if (messages.size <= 1) return messages
    val seen = HashSet<String>()
    val out = ArrayList<ChatMessage>(messages.size)
    for (msg in messages) {
        val id = msg._id?.trim().orEmpty()
        if (id.isEmpty() || seen.add(id)) {
            out.add(msg)
        }
    }
    return out
}

/** In-place swap of a matching optimistic row for the confirmed server message (socket/HTTP). */
internal data class PendingOutgoingReplacement(
    val messages: List<ChatMessage>,
    val pendingId: String,
    val serverId: String,
    val replacedIndex: Int,
)

internal fun replaceMatchingPendingOutgoing(
    current: List<ChatMessage>,
    incoming: ChatMessage,
    currentUserId: String,
): PendingOutgoingReplacement? {
    val selfId = currentUserId.trim()
    val serverId = incoming._id?.trim().orEmpty()
    if (selfId.isEmpty() || serverId.isEmpty() || serverId.startsWith("pending-")) return null
    if (incoming.senderId.trim() != selfId) return null
    val idx = current.indexOfFirst { msg ->
        val pendingId = msg._id?.trim().orEmpty()
        pendingId.startsWith("pending-") &&
            msg.senderId.trim() == selfId &&
            outgoingTextsMatch(msg, incoming)
    }
    if (idx < 0) return null
    val pendingId = current[idx]._id?.trim().orEmpty()
    if (pendingId.isEmpty()) return null
    val updated = current.toMutableList()
    updated[idx] = mergeOutgoingConfirmation(current[idx], incoming)
    return PendingOutgoingReplacement(
        messages = updated,
        pendingId = pendingId,
        serverId = serverId,
        replacedIndex = idx,
    )
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
        !confirmed.any { sent -> outgoingTextsMatch(msg, sent) }
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
            updated[existingIndex] = incoming.mergeIncomingChatUpdate(current[existingIndex])
            idIndex?.put(id, existingIndex)
            return MessageUpsertResult(
                messages = updated,
                newestMessageKey = null,
            )
        }
        knownMessageIds.add(id)
        val next = listOf(incoming.normalizeEditedAtForDisplay()) + current
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
    val normalized = incoming.normalizeEditedAtForDisplay()
    return MessageUpsertResult(
        messages = listOf(normalized) + current,
        newestMessageKey = fallbackMessageKey(normalized),
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

/** Index of the only changed row, or null when size differs or multiple rows changed. */
internal fun findSingleChangedMessageIndex(
    before: List<ChatMessage>,
    after: List<ChatMessage>,
): Int? {
    if (before.size != after.size) return null
    var changedIndex: Int? = null
    for (i in before.indices) {
        if (before[i] == after[i]) continue
        if (changedIndex != null) return null
        changedIndex = i
    }
    return changedIndex
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
    val capped = dedupeMessagesByIdNewestFirst(capNewestFirst(messages, maxMessages))
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

/** Socket `room:join` order — selected room first for lowest message latency. */
internal fun orderRealtimeSubscriptionRoomIds(
    rooms: List<ChatRoomDto>,
    selectedRoomId: String?,
    raidRoomId: String?,
    hubRoomId: String?,
    subscribeAllRooms: Boolean,
): List<String> =
    buildList {
        val selected = selectedRoomId?.trim().orEmpty()
        if (selected.isNotEmpty()) add(selected)
        val raid = raidRoomId?.trim().orEmpty()
        if (raid.isNotEmpty() && raid !in this) add(raid)
        val hub = hubRoomId?.trim().orEmpty()
        if (hub.isNotEmpty() && hub !in this) add(hub)
        if (subscribeAllRooms) {
            rooms.forEach { room ->
                val id = room.id.trim()
                if (id.isNotEmpty() && id !in this) add(id)
            }
        } else {
            rooms.forEach { room ->
                val id = room.id.trim()
                if (id.isNotEmpty() && room.unreadCount > 0 && id !in this) {
                    add(id)
                }
            }
        }
        if (rooms.isNotEmpty() && isEmpty()) {
            rooms.firstOrNull { it.id == selectedRoomId }?.id?.let { add(it) }
        }
    }
