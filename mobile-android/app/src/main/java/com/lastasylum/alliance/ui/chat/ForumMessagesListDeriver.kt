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

    fun timelineIndexForMessageId(id: String): Int? = messageIdToLazyIndex[id]

    /** Lazy index under [reverseLayout] (0 = newest at visual bottom). */
    fun fullLazyIndexForMessageId(id: String): Int? =
        timelineIndexForMessageId(id)?.let { timeline.lastIndex - it }

    fun bottomLazyIndex(): Int? = if (timeline.isEmpty()) null else 0
}

fun duplicateForumMessageIdsIn(messages: List<TeamForumMessageDto>): Set<String> {
    if (messages.size <= 1) return emptySet()
    val counts = HashMap<String, Int>()
    for (msg in messages) {
        val id = msg.id.trim()
        if (id.isEmpty()) continue
        counts[id] = (counts[id] ?: 0) + 1
    }
    return counts.filterValues { it > 1 }.keys
}

fun forumTimelineMessageItemKey(
    timelineIndex: Int,
    messageIndex: Int,
    messageId: String,
    duplicateIds: Set<String> = emptySet(),
    duplicateLazyKeys: Set<String> = emptySet(),
): String {
    val base = "msg:$messageId"
    val id = messageId.trim()
    val needsIndex = (id.isNotEmpty() && id in duplicateIds) || base in duplicateLazyKeys
    return if (needsIndex) {
        "t:$timelineIndex:m:$messageIndex:$base"
    } else {
        base
    }
}

/** LazyColumn keys that appear more than once in [timeline] (socket/REST race). */
fun duplicateForumLazyKeysInTimeline(timeline: List<ForumTimelineEntry>): Set<String> {
    if (timeline.size <= 1) return emptySet()
    val seen = HashSet<String>()
    val dupes = HashSet<String>()
    timeline.forEachIndexed { idx, entry ->
        val key = when (entry) {
            is ForumTimelineEntry.DaySeparator -> "day:$idx:${entry.label.trim()}"
            is ForumTimelineEntry.Message -> "msg:${entry.messageId}"
        }
        if (!seen.add(key)) {
            dupes.add(key)
        }
    }
    return dupes
}

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

/** Forum rows read [messages] by index — timeline unchanged when only DTO fields change. */
fun forumDerivedAfterMessageContentPatch(
    previousDerived: ForumMessagesListDerived,
    messages: List<TeamForumMessageDto>,
): ForumMessagesListDerived {
    if (messages.isEmpty()) return ForumMessagesListDerived.Empty
    if (previousDerived.timeline.isEmpty() ||
        previousDerived.clusterFlags.size != messages.size
    ) {
        return buildForumMessagesListDerived(messages)
    }
    return previousDerived
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
