package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage

/** Max messages to derive synchronously on the main thread (no frame wait). */
const val CHAT_LIST_DERIVE_SYNC_MAX = 160

/**
 * Precomputed list layout for the chat [androidx.compose.foundation.lazy.LazyColumn].
 * [clusterTopSpacingDp] values are Compose dp units (see [clusterTopSpacingAt]).
 */
data class ChatMessagesListDerived(
    val timeline: List<ChatTimelineEntry>,
    val clusterFlags: List<ChatMessageClusterFlags>,
    /** Per [timeline] index; 0 for day separators. */
    val clusterTopSpacingDp: List<Int>,
) {
    companion object {
        val Empty = ChatMessagesListDerived(
            timeline = emptyList(),
            clusterFlags = emptyList(),
            clusterTopSpacingDp = emptyList(),
        )
    }
}

fun clusterTopSpacingAt(derived: ChatMessagesListDerived, timelineIndex: Int): Int =
    derived.clusterTopSpacingDp.getOrElse(timelineIndex) { 10 }

fun chatTimelineDaySeparatorKey(label: String): String =
    "day:${label.trim()}"

fun buildChatMessagesListDerived(messages: List<ChatMessage>): ChatMessagesListDerived {
    if (messages.isEmpty()) return ChatMessagesListDerived.Empty
    val timeline = buildChatTimeline(messages)
    val clusterFlags = List(messages.size) { index ->
        ChatMessageClusterFlags(
            showHeader = chatMessageShowsClusterHeaderNewestFirst(messages, index),
            isChainBottom = chatMessageIsClusterChainBottomNewestFirst(messages, index),
            tightInnerTop = chatMessageClusterTightInnerTopNewestFirst(messages, index),
        )
    }
    val clusterTopSpacingDp = buildClusterTopSpacingDp(timeline)
    return ChatMessagesListDerived(
        timeline = timeline,
        clusterFlags = clusterFlags,
        clusterTopSpacingDp = clusterTopSpacingDp,
    )
}

/**
 * Fast path when a single message was prepended at the head (send / live socket).
 * Falls back to full rebuild if the diff is not a simple prepend.
 */
/** O(n) spacing (was O(n²) via [chatBubbleClusterTopSpacing] per row). */
internal fun buildClusterTopSpacingDp(timeline: List<ChatTimelineEntry>): List<Int> {
    val n = timeline.size
    if (n == 0) return emptyList()
    val result = IntArray(n)
    var lookAheadIsDay = false
    var lookAheadSender: String? = null
    for (idx in n - 1 downTo 0) {
        when (val entry = timeline[idx]) {
            is ChatTimelineEntry.DaySeparator -> {
                result[idx] = 0
                lookAheadIsDay = true
                lookAheadSender = null
            }
            is ChatTimelineEntry.ChatMessageItem -> {
                val sid = entry.message.senderId.trim()
                result[idx] = clusterTopSpacingFromLookAhead(sid, lookAheadIsDay, lookAheadSender)
                lookAheadIsDay = false
                lookAheadSender = sid.takeIf { it.isNotEmpty() }
            }
            is ChatTimelineEntry.ChatAlbumItem -> {
                val sid = entry.representativeMessage.senderId.trim()
                result[idx] = clusterTopSpacingFromLookAhead(sid, lookAheadIsDay, lookAheadSender)
                lookAheadIsDay = false
                lookAheadSender = sid.takeIf { it.isNotEmpty() }
            }
        }
    }
    return result.toList()
}

private fun clusterTopSpacingFromLookAhead(
    senderId: String,
    lookAheadIsDay: Boolean,
    lookAheadSender: String?,
): Int {
    if (senderId.isEmpty()) return 10
    if (lookAheadIsDay) return 14
    val other = lookAheadSender?.trim().orEmpty()
    if (other.isEmpty()) return 10
    return if (other == senderId) 3 else 14
}

private fun shiftTimelineEntryForPrepend(entry: ChatTimelineEntry): ChatTimelineEntry =
    when (entry) {
        is ChatTimelineEntry.DaySeparator -> entry
        is ChatTimelineEntry.ChatMessageItem ->
            entry.copy(messageIndex = entry.messageIndex + 1)
        is ChatTimelineEntry.ChatAlbumItem ->
            entry.copy(
                firstMessageIndex = entry.firstMessageIndex + 1,
                messageIndices = entry.messageIndices.map { it + 1 },
            )
    }

private fun canIncrementallyPrependDerived(
    previousDerived: ChatMessagesListDerived,
    previousMessages: List<ChatMessage>,
    messages: List<ChatMessage>,
): Boolean {
    if (messages.size != previousMessages.size + 1) return false
    if (messages.drop(1) != previousMessages) return false
    if (previousMessages.isEmpty() || previousDerived.timeline.isEmpty()) return false
    if (chatMessageIsAlbumCandidate(messages[0])) return false
    val first = previousDerived.timeline.firstOrNull() ?: return true
    if (first is ChatTimelineEntry.ChatAlbumItem && first.firstMessageIndex <= 1) {
        return false
    }
    return true
}

fun buildChatMessagesListDerivedAfterPrepend(
    previousDerived: ChatMessagesListDerived,
    previousMessages: List<ChatMessage>,
    messages: List<ChatMessage>,
): ChatMessagesListDerived {
    if (messages.isEmpty()) return ChatMessagesListDerived.Empty
    if (!canIncrementallyPrependDerived(previousDerived, previousMessages, messages)) {
        return buildChatMessagesListDerived(messages)
    }
    val newTimeline = ArrayList<ChatTimelineEntry>(previousDerived.timeline.size + 2)
    val d0 = chatDayKey(messages[0].createdAt)
    val d1 = chatDayKey(messages[1].createdAt)
    if (d0 != null && d1 != null && d0 != d1) {
        val label = formatChatDaySeparator(messages[1].createdAt)
        if (label.isNotBlank()) {
            newTimeline.add(ChatTimelineEntry.DaySeparator(label))
        }
    }
    newTimeline.add(ChatTimelineEntry.ChatMessageItem(messages[0], 0))
    previousDerived.timeline.forEach { entry ->
        newTimeline.add(shiftTimelineEntryForPrepend(entry))
    }
    val clusterFlags = ArrayList<ChatMessageClusterFlags>(messages.size)
    clusterFlags.add(
        ChatMessageClusterFlags(
            showHeader = chatMessageShowsClusterHeaderNewestFirst(messages, 0),
            isChainBottom = chatMessageIsClusterChainBottomNewestFirst(messages, 0),
            tightInnerTop = chatMessageClusterTightInnerTopNewestFirst(messages, 0),
        ),
    )
    clusterFlags.add(
        ChatMessageClusterFlags(
            showHeader = chatMessageShowsClusterHeaderNewestFirst(messages, 1),
            isChainBottom = chatMessageIsClusterChainBottomNewestFirst(messages, 1),
            tightInnerTop = chatMessageClusterTightInnerTopNewestFirst(messages, 1),
        ),
    )
    for (i in 2 until messages.size) {
        clusterFlags.add(previousDerived.clusterFlags[i - 1])
    }
    val clusterTopSpacingDp = buildClusterTopSpacingDp(newTimeline)
    return ChatMessagesListDerived(
        timeline = newTimeline,
        clusterFlags = clusterFlags,
        clusterTopSpacingDp = clusterTopSpacingDp,
    )
}
