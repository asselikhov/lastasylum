package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage

data class ChatMessagesListDerived(
    val timeline: List<ChatTimelineEntry>,
    val clusterFlags: List<ChatMessageClusterFlags>,
) {
    companion object {
        val Empty = ChatMessagesListDerived(
            timeline = emptyList(),
            clusterFlags = emptyList(),
        )
    }
}

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
    return ChatMessagesListDerived(timeline = timeline, clusterFlags = clusterFlags)
}
