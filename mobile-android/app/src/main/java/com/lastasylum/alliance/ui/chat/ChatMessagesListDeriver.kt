package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage

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
    val clusterTopSpacingDp = List(timeline.size) { idx ->
        when (val entry = timeline[idx]) {
            is ChatTimelineEntry.DaySeparator -> 0
            is ChatTimelineEntry.ChatMessageItem -> {
                chatBubbleClusterTopSpacing(timeline, idx, entry.message).value.toInt()
            }
            is ChatTimelineEntry.ChatAlbumItem -> {
                chatBubbleClusterTopSpacing(timeline, idx, entry.representativeMessage).value.toInt()
            }
        }
    }
    return ChatMessagesListDerived(
        timeline = timeline,
        clusterFlags = clusterFlags,
        clusterTopSpacingDp = clusterTopSpacingDp,
    )
}
