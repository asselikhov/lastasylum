package com.lastasylum.alliance.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.data.teams.TeamForumMessageDto

@Composable
fun rememberForumMessageClusterFlags(
    messages: List<TeamForumMessageDto>,
): List<ForumMessageClusterFlags> =
    remember(messages) {
        List(messages.size) { index ->
            ForumMessageClusterFlags(
                showHeader = forumMessageShowsClusterHeader(messages, index),
                isChainBottom = forumMessageIsClusterChainBottom(messages, index),
                tightInnerTop = forumMessageClusterTightInnerTop(messages, index),
                topSpacing = forumBubbleClusterTopSpacing(messages, index),
            )
        }
    }

/** Forum list is oldest-first (unlike main chat). */
fun forumMessageIsClusterChainBottom(messages: List<TeamForumMessageDto>, index: Int): Boolean {
    if (messages.isEmpty() || index !in messages.indices) return true
    if (index == messages.lastIndex) return true
    val m = messages[index]
    val next = messages[index + 1]
    if (m.senderUserId.trim() != next.senderUserId.trim()) return true
    val d0 = chatDayKey(m.createdAt)
    val d1 = chatDayKey(next.createdAt)
    if (d0 != null && d1 != null && d0 != d1) return true
    return false
}

fun forumMessageShowsClusterHeader(messages: List<TeamForumMessageDto>, index: Int): Boolean {
    if (index !in messages.indices) return true
    if (index == 0) return true
    val m = messages[index]
    val prev = messages[index - 1]
    if (m.senderUserId.trim() != prev.senderUserId.trim()) return true
    val d0 = chatDayKey(m.createdAt)
    val d1 = chatDayKey(prev.createdAt)
    if (d0 != null && d1 != null && d0 != d1) return true
    return false
}

fun forumBubbleClusterTopSpacing(messages: List<TeamForumMessageDto>, index: Int): Dp {
    if (index <= 0) return 8.dp
    val m = messages[index]
    val prev = messages[index - 1]
    val same = m.senderUserId.trim() == prev.senderUserId.trim() && m.senderUserId.isNotBlank()
    return if (same) 2.dp else 10.dp
}

fun forumMessageClusterTightInnerTop(messages: List<TeamForumMessageDto>, index: Int): Boolean {
    if (index <= 0) return false
    val m = messages[index]
    val prev = messages[index - 1]
    if (m.senderUserId.trim() != prev.senderUserId.trim()) return false
    val d0 = chatDayKey(m.createdAt)
    val d1 = chatDayKey(prev.createdAt)
    return d0 == null || d1 == null || d0 == d1
}
