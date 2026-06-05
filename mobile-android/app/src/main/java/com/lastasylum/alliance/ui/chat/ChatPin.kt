package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatRoomPinChangedEvent
import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto
import com.lastasylum.alliance.data.chat.stickers.StickerPacks
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamForumTopicPinChangedEvent
import java.time.Instant

data class TopicPinSnapshot(
    val pinnedMessageId: String?,
    val pinnedAt: String?,
    val pinnedByUserId: String?,
    val pinnedMessage: PinnedMessagePreviewDto?,
)

fun PinnedMessagePreviewDto.resolvedThumbnailUrl(): String? {
    val raw = imageThumbnailUrl?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return resolvedChatAttachmentImageUrl(raw)
}

private fun firstForumImageRelativeUrl(msg: TeamForumMessageDto): String? {
    msg.imageRelativeUrls.firstOrNull { it.isNotBlank() }?.let { return it }
    return msg.imageRelativeUrl?.trim()?.takeIf { it.isNotEmpty() }
}

fun ChatMessage.toPinnedPreview(): PinnedMessagePreviewDto? {
    val id = _id?.trim().orEmpty()
    if (id.isEmpty()) return null
    val imageUrl = attachments.firstOrNull { it.kind == "image" && it.url.isNotBlank() }?.url
    val hasImage = imageUrl != null
    return PinnedMessagePreviewDto(
        id = id,
        text = text,
        senderUsername = senderUsername,
        senderTeamTag = senderTeamTag,
        senderServerNumber = senderServerNumber,
        createdAt = createdAt ?: Instant.now().toString(),
        editedAt = editedAt,
        hasImage = hasImage,
        isSticker = StickerPacks.stemForMessage(text) != null,
        imageThumbnailUrl = imageUrl,
        pinnedByUsername = null,
    )
}

fun TeamForumMessageDto.toPinnedPreview(): PinnedMessagePreviewDto {
    val imageUrl = firstForumImageRelativeUrl(this)
    return PinnedMessagePreviewDto(
        id = id,
        text = text,
        senderUsername = senderUsername,
        senderTeamTag = senderTeamTag,
        senderServerNumber = senderServerNumber,
        createdAt = createdAt,
        editedAt = editedAt,
        hasImage = imageUrl != null,
        isSticker = StickerPacks.stemForMessage(text) != null,
        imageThumbnailUrl = imageUrl,
        pinnedByUsername = null,
    )
}

fun ChatRoomDto.withOptimisticPin(
    messageId: String,
    preview: PinnedMessagePreviewDto,
    pinnedByUserId: String,
): ChatRoomDto = copy(
    pinnedMessageId = messageId,
    pinnedAt = Instant.now().toString(),
    pinnedByUserId = pinnedByUserId,
    pinnedMessage = preview,
)

fun ChatRoomDto.withOptimisticUnpin(): ChatRoomDto = copy(
    pinnedMessageId = null,
    pinnedAt = null,
    pinnedByUserId = null,
    pinnedMessage = null,
    pinnedMessages = emptyList(),
)

/** Remove one pin from history; promote the next head entry when the active pin was removed. */
fun ChatRoomDto.withOptimisticUnpinOne(
    messageId: String,
    localHistory: List<PinnedMessagePreviewDto> = pinnedMessagesOrEmpty(),
): ChatRoomDto {
    val id = messageId.trim()
    if (id.isEmpty()) return this
    val history = removePinFromHistory(localHistory, id)
    if (pinnedMessageId?.trim() != id) {
        return copy(pinnedMessages = history)
    }
    val next = history.firstOrNull()
    return if (next != null) {
        copy(
            pinnedMessageId = next.id,
            pinnedMessage = next,
            pinnedMessages = history,
        )
    } else {
        withOptimisticUnpin()
    }
}

fun ChatRoomDto.mergePinFromPrevious(
    previous: ChatRoomDto?,
    pinOperationInFlight: Boolean = false,
): ChatRoomDto {
    if (previous == null) return this
    if (pinOperationInFlight) {
        val prevPinId = previous.pinnedMessageId?.trim().orEmpty()
        val serverPinId = pinnedMessageId?.trim().orEmpty()
        if (prevPinId != serverPinId) {
            return copy(
                pinnedMessageId = previous.pinnedMessageId,
                pinnedAt = previous.pinnedAt,
                pinnedByUserId = previous.pinnedByUserId,
                pinnedMessage = when {
                    prevPinId.isEmpty() -> null
                    previous.pinnedMessage != null -> previous.pinnedMessage
                    else -> pinnedMessage
                },
                pinnedMessages = previous.pinnedMessagesOrEmpty(),
            )
        }
    }
    val pinId = pinnedMessageId?.trim().orEmpty()
    if (pinId.isEmpty()) return this
    if (pinnedMessage != null) return this
    val prevId = previous.pinnedMessageId?.trim().orEmpty()
    if (pinId == prevId && previous.pinnedMessage != null) {
        return copy(
            pinnedMessage = previous.pinnedMessage,
            pinnedAt = pinnedAt ?: previous.pinnedAt,
            pinnedByUserId = pinnedByUserId ?: previous.pinnedByUserId,
        )
    }
    return this
}

fun ChatRoomDto.mergePinFromEvent(event: ChatRoomPinChangedEvent): ChatRoomDto {
    val eventPinId = event.pinnedMessageId?.trim().orEmpty()
    val keptPreview = when {
        event.pinnedMessage != null -> event.pinnedMessage
        eventPinId.isEmpty() -> null
        else -> event.pinnedMessages.find { it.id.trim() == eventPinId }
            ?: pinnedMessage?.takeIf { it.id.trim() == eventPinId }
    }
    val history = event.pinnedMessages.ifEmpty {
        keptPreview?.let { listOf(it) } ?: emptyList()
    }
    return copy(
        pinnedMessageId = event.pinnedMessageId,
        pinnedAt = event.pinnedAt,
        pinnedByUserId = event.pinnedByUserId,
        pinnedMessage = keptPreview,
        pinnedMessages = history,
    )
}

fun ensureRoomPinPreview(
    room: ChatRoomDto,
    preview: PinnedMessagePreviewDto?,
    pinnedByUserId: String,
): ChatRoomDto {
    val pinId = room.pinnedMessageId?.trim().orEmpty()
    if (pinId.isEmpty()) return room
    if (room.pinnedMessage != null) return room
    if (preview != null && preview.id == pinId) {
        return room.withOptimisticPin(pinId, preview, pinnedByUserId)
    }
    return room
}

fun resolveChatPinnedPreview(
    pinnedMessageId: String?,
    pinnedMessage: PinnedMessagePreviewDto?,
    messages: List<ChatMessage>,
): PinnedMessagePreviewDto? {
    val id = pinnedMessageId?.trim().orEmpty()
    if (id.isEmpty()) return null
    messages.find { it._id?.trim() == id }?.toPinnedPreview()?.let { return it }
    return pinnedMessage?.takeIf { it.id.trim() == id }
}

fun resolveForumPinnedPreview(
    pinnedMessageId: String?,
    pinnedMessage: PinnedMessagePreviewDto?,
    messages: List<TeamForumMessageDto>,
): PinnedMessagePreviewDto? {
    val id = pinnedMessageId?.trim().orEmpty()
    if (id.isEmpty()) return null
    messages.find { it.id.trim() == id }?.toPinnedPreview()?.let { return it }
    return pinnedMessage?.takeIf { it.id.trim() == id }
}

enum class PinPreviewDisplayState {
    Ok,
    Deleted,
    Unavailable,
}

fun chatPinPreviewDisplayState(
    preview: PinnedMessagePreviewDto,
    messages: List<ChatMessage>,
    serverPreview: PinnedMessagePreviewDto?,
    pinnedMessageId: String?,
): PinPreviewDisplayState {
    val id = preview.id.trim()
    if (id.isEmpty()) return PinPreviewDisplayState.Ok
    val msg = messages.find { it._id?.trim() == id }
    if (msg != null) {
        return if (msg.deletedAt != null) PinPreviewDisplayState.Deleted else PinPreviewDisplayState.Ok
    }
    if (serverPreview?.id?.trim() == id) return PinPreviewDisplayState.Ok
    if (pinnedMessageId?.trim() == id) return PinPreviewDisplayState.Unavailable
    return PinPreviewDisplayState.Ok
}

fun forumPinPreviewDisplayState(
    preview: PinnedMessagePreviewDto,
    messages: List<TeamForumMessageDto>,
    serverPreview: PinnedMessagePreviewDto?,
    pinnedMessageId: String?,
): PinPreviewDisplayState {
    val id = preview.id.trim()
    if (id.isEmpty()) return PinPreviewDisplayState.Ok
    val msg = messages.find { it.id.trim() == id }
    if (msg != null) {
        return if (!msg.deletedAt.isNullOrBlank()) PinPreviewDisplayState.Deleted else PinPreviewDisplayState.Ok
    }
    if (serverPreview?.id?.trim() == id) return PinPreviewDisplayState.Ok
    if (pinnedMessageId?.trim() == id) return PinPreviewDisplayState.Unavailable
    return PinPreviewDisplayState.Ok
}

const val PIN_HISTORY_MAX = 15

/** Most recent pin first; dedupes by message id and caps length. */
fun pushPinHistory(
    history: List<PinnedMessagePreviewDto>,
    preview: PinnedMessagePreviewDto,
): List<PinnedMessagePreviewDto> {
    val id = preview.id.trim()
    if (id.isEmpty()) return history
    return (listOf(preview) + history.filter { it.id.trim() != id }).take(PIN_HISTORY_MAX)
}

/** Preview shown in the pinned bar for the current cycle index. */
fun pinBarPreviewAtIndex(
    history: List<PinnedMessagePreviewDto>,
    barIndex: Int,
    serverPreview: PinnedMessagePreviewDto?,
    activePinId: String? = null,
): PinnedMessagePreviewDto? {
    val pinId = activePinId?.trim().orEmpty()
    if (barIndex == 0 && pinId.isNotEmpty()) {
        serverPreview?.takeIf { it.id.trim() == pinId }?.let { return it }
        history.firstOrNull { it.id.trim() == pinId }?.let { return it }
    }
    if (history.isNotEmpty() && barIndex in history.indices) return history[barIndex]
    return serverPreview ?: history.firstOrNull()
}

/** Drop a deleted/unpinned message from local pin history. */
fun removePinFromHistory(
    history: List<PinnedMessagePreviewDto>,
    messageId: String,
): List<PinnedMessagePreviewDto> {
    val id = messageId.trim()
    if (id.isEmpty()) return history
    return history.filter { it.id.trim() != id }
}

/** Advance to the next pin in Telegram-style history cycling. */
fun advancePinBarIndex(
    history: List<PinnedMessagePreviewDto>,
    currentIndex: Int,
): Int {
    if (history.size <= 1) return currentIndex.coerceAtLeast(0)
    return (currentIndex + 1) % history.size
}

/** Badge count when multiple pins are in local history (0 hides badge). */
fun pinHistoryDisplayCount(history: List<PinnedMessagePreviewDto>): Int =
    if (history.size > 1) history.size else 0

/**
 * Merge server pin into local history.
 * Returns updated history and whether the bar index should reset to 0 (new active pin).
 */
fun syncRoomPinHistory(
    history: List<PinnedMessagePreviewDto>,
    serverPreview: PinnedMessagePreviewDto?,
    pinnedMessageId: String?,
): Pair<List<PinnedMessagePreviewDto>, Boolean> {
    val pinId = pinnedMessageId?.trim().orEmpty()
    if (pinId.isEmpty()) return history to false
    val preview = serverPreview ?: history.find { it.id.trim() == pinId } ?: return history to false
    val headId = history.firstOrNull()?.id?.trim().orEmpty()
    if (headId == pinId) {
        val refreshed = if (history.isNotEmpty()) {
            history.toMutableList().apply { this[0] = preview }
        } else {
            listOf(preview)
        }
        return refreshed to false
    }
    return pushPinHistory(history, preview) to true
}

/** Refresh cached previews from loaded messages when available. */
fun refreshPinHistoryPreviews(
    history: List<PinnedMessagePreviewDto>,
    messages: List<ChatMessage>,
): List<PinnedMessagePreviewDto> =
    history.map { entry ->
        messages.find { it._id?.trim() == entry.id.trim() }?.toPinnedPreview() ?: entry
    }

fun isPinnedPreviewLikelyDeleted(
    preview: PinnedMessagePreviewDto,
    messages: List<ChatMessage>,
    serverPreview: PinnedMessagePreviewDto? = null,
    pinnedMessageId: String? = null,
): Boolean =
    chatPinPreviewDisplayState(preview, messages, serverPreview, pinnedMessageId) ==
        PinPreviewDisplayState.Deleted

fun isForumPinnedPreviewLikelyDeleted(
    preview: PinnedMessagePreviewDto,
    messages: List<TeamForumMessageDto>,
    serverPreview: PinnedMessagePreviewDto? = null,
    pinnedMessageId: String? = null,
): Boolean =
    forumPinPreviewDisplayState(preview, messages, serverPreview, pinnedMessageId) ==
        PinPreviewDisplayState.Deleted

fun isPinnedPreviewUnavailable(
    preview: PinnedMessagePreviewDto,
    messages: List<ChatMessage>,
    serverPreview: PinnedMessagePreviewDto?,
    pinnedMessageId: String?,
): Boolean =
    chatPinPreviewDisplayState(preview, messages, serverPreview, pinnedMessageId) ==
        PinPreviewDisplayState.Unavailable

fun isForumPinnedPreviewUnavailable(
    preview: PinnedMessagePreviewDto,
    messages: List<TeamForumMessageDto>,
    serverPreview: PinnedMessagePreviewDto?,
    pinnedMessageId: String?,
): Boolean =
    forumPinPreviewDisplayState(preview, messages, serverPreview, pinnedMessageId) ==
        PinPreviewDisplayState.Unavailable

data class TopicPinSnapshotWithHistory(
    val snapshot: TopicPinSnapshot,
    val pinnedMessages: List<PinnedMessagePreviewDto>,
)

fun TopicPinSnapshot.mergePinFromEvent(event: TeamForumTopicPinChangedEvent): TopicPinSnapshot =
    mergePinFromEventWithHistory(event).snapshot

fun TopicPinSnapshot.mergePinFromEventWithHistory(
    event: TeamForumTopicPinChangedEvent,
): TopicPinSnapshotWithHistory {
    val eventPinId = event.pinnedMessageId?.trim().orEmpty()
    val keptPreview = when {
        event.pinnedMessage != null -> event.pinnedMessage
        eventPinId.isEmpty() -> null
        else -> event.pinnedMessages.find { it.id.trim() == eventPinId }
            ?: pinnedMessage?.takeIf { it.id.trim() == eventPinId }
    }
    val history = event.pinnedMessages.ifEmpty {
        keptPreview?.let { listOf(it) } ?: emptyList()
    }
    return TopicPinSnapshotWithHistory(
        snapshot = copy(
            pinnedMessageId = event.pinnedMessageId,
            pinnedAt = event.pinnedAt,
            pinnedByUserId = event.pinnedByUserId,
            pinnedMessage = keptPreview,
        ),
        pinnedMessages = history,
    )
}

fun serverPinHistoryFromRoom(room: ChatRoomDto): List<PinnedMessagePreviewDto> =
    room.pinnedMessagesOrEmpty().ifEmpty {
        room.pinnedMessage?.let { listOf(it) } ?: emptyList()
    }

/** Union server and local pin history; server preview wins per id. */
fun mergePinHistory(
    server: List<PinnedMessagePreviewDto>,
    local: List<PinnedMessagePreviewDto>,
): List<PinnedMessagePreviewDto> {
    if (server.isEmpty()) return local
    if (local.isEmpty()) return server
    val serverById = server.associateBy { it.id.trim() }
    val merged = LinkedHashMap<String, PinnedMessagePreviewDto>()
    for (entry in server) {
        val id = entry.id.trim()
        if (id.isNotEmpty()) merged[id] = entry
    }
    for (entry in local) {
        val id = entry.id.trim()
        if (id.isEmpty() || id in merged) continue
        merged[id] = entry
    }
    return merged.values.toList()
}

fun serverPinHistoryFromTopic(topic: TeamForumTopicDto): List<PinnedMessagePreviewDto> =
    topic.pinnedMessages.ifEmpty {
        topic.pinnedMessage?.let { listOf(it) } ?: emptyList()
    }

fun TopicPinSnapshot.mergePinFromPrevious(
    previous: TopicPinSnapshot?,
    pinOperationInFlight: Boolean = false,
): TopicPinSnapshot {
    if (previous == null) return this
    if (pinOperationInFlight) {
        val prevPinId = previous.pinnedMessageId?.trim().orEmpty()
        val serverPinId = pinnedMessageId?.trim().orEmpty()
        if (prevPinId != serverPinId) {
            return copy(
                pinnedMessageId = previous.pinnedMessageId,
                pinnedAt = previous.pinnedAt,
                pinnedByUserId = previous.pinnedByUserId,
                pinnedMessage = when {
                    prevPinId.isEmpty() -> null
                    previous.pinnedMessage != null -> previous.pinnedMessage
                    else -> pinnedMessage
                },
            )
        }
    }
    val pinId = pinnedMessageId?.trim().orEmpty()
    if (pinId.isEmpty()) return this
    if (pinnedMessage != null) return this
    val prevId = previous.pinnedMessageId?.trim().orEmpty()
    if (pinId == prevId && previous.pinnedMessage != null) {
        return copy(
            pinnedMessage = previous.pinnedMessage,
            pinnedAt = pinnedAt ?: previous.pinnedAt,
            pinnedByUserId = pinnedByUserId ?: previous.pinnedByUserId,
        )
    }
    return this
}

fun formatPinnedMetaLine(
    pinnedAt: String?,
    pinnedByUsername: String?,
    pinnedByUserId: String?,
    currentUserId: String,
    youLabel: String,
    userTemplate: (String) -> String,
    formatTime: (String) -> String,
): String? {
    val at = pinnedAt?.trim().orEmpty()
    val timePart = if (at.isNotEmpty()) formatTime(at) else null
    val pinUserId = pinnedByUserId?.trim().orEmpty()
    val name = pinnedByUsername?.trim().orEmpty()
    val who = when {
        pinUserId.isNotEmpty() && pinUserId == currentUserId.trim() -> youLabel
        name.isNotEmpty() -> userTemplate(name)
        else -> null
    }
    return when {
        who != null && timePart != null -> "$who · $timePart"
        who != null -> who
        timePart != null -> timePart
        else -> null
    }
}
