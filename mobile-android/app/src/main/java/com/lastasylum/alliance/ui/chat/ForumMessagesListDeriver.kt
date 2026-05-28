package com.lastasylum.alliance.ui.chat

import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.data.teams.TeamForumMessageDto

sealed class ForumTimelineEntry {
    data class DaySeparator(val label: String) : ForumTimelineEntry()
    data class Message(val messageIndex: Int, val messageId: String) : ForumTimelineEntry()
}

data class ForumMessagesListDerived(
    val timeline: List<ForumTimelineEntry>,
    val clusterFlags: List<ForumMessageClusterFlags>,
    val messageIdToLazyIndex: Map<String, Int>,
) {
    companion object {
        val Empty = ForumMessagesListDerived(
            timeline = emptyList(),
            clusterFlags = emptyList(),
            messageIdToLazyIndex = emptyMap(),
        )
    }

    fun lazyIndexForMessageId(id: String): Int? = messageIdToLazyIndex[id]

    fun fullLazyIndexForMessageId(id: String, hasMoreOlder: Boolean): Int? =
        lazyIndexForMessageId(id)?.let { forumLazyListLoadOlderOffset(hasMoreOlder) + it }

    fun bottomLazyIndex(hasMoreOlder: Boolean): Int? {
        if (timeline.isEmpty()) return null
        return forumLazyListLoadOlderOffset(hasMoreOlder) + timeline.lastIndex
    }
}

fun forumLazyListLoadOlderOffset(hasMoreOlder: Boolean): Int = if (hasMoreOlder) 1 else 0

fun buildForumMessagesListDerived(
    messages: List<TeamForumMessageDto>,
): ForumMessagesListDerived {
    if (messages.isEmpty()) return ForumMessagesListDerived.Empty
    val clusterFlags = List(messages.size) { index ->
        ForumMessageClusterFlags(
            showHeader = forumMessageShowsClusterHeader(messages, index),
            isChainBottom = forumMessageIsClusterChainBottom(messages, index),
            tightInnerTop = forumMessageClusterTightInnerTop(messages, index),
            topSpacing = forumBubbleClusterTopSpacing(messages, index),
        )
    }
    val timeline = mutableListOf<ForumTimelineEntry>()
    val idToIndex = mutableMapOf<String, Int>()
    messages.forEachIndexed { idx, msg ->
        val prev = messages.getOrNull(idx - 1)
        val dayCurr = chatDayKey(msg.createdAt)
        val dayPrev = chatDayKey(prev?.createdAt)
        if (idx == 0 || dayCurr != dayPrev) {
            val sep = formatChatDaySeparator(msg.createdAt)
            if (sep.isNotBlank()) {
                timeline += ForumTimelineEntry.DaySeparator(sep)
            }
        }
        idToIndex[msg.id] = timeline.size
        timeline += ForumTimelineEntry.Message(idx, msg.id)
    }
    return ForumMessagesListDerived(
        timeline = timeline,
        clusterFlags = clusterFlags,
        messageIdToLazyIndex = idToIndex,
    )
}

private fun forumBubbleClusterTopSpacing(messages: List<TeamForumMessageDto>, index: Int): androidx.compose.ui.unit.Dp {
    if (index <= 0) return 10.dp
    val m = messages[index]
    val prev = messages[index - 1]
    val sameSender = m.senderUserId.trim() == prev.senderUserId.trim() && m.senderUserId.isNotBlank()
    if (!sameSender) return 14.dp
    val d0 = chatDayKey(m.createdAt)
    val d1 = chatDayKey(prev.createdAt)
    if (d0 != null && d1 != null && d0 != d1) return 14.dp
    return 3.dp
}
