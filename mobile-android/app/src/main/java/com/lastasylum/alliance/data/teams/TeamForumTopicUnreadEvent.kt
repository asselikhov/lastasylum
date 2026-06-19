package com.lastasylum.alliance.data.teams

/** Personal fanout when forum topic unread snapshot changes (parity with chat `rooms:unread`). */
data class TeamForumTopicUnreadEvent(
    val teamId: String,
    val topicId: String,
    val unreadCount: Int,
    val lastReadMessageId: String?,
)
