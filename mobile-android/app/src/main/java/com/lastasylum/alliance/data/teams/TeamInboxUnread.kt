package com.lastasylum.alliance.data.teams

import com.lastasylum.alliance.data.effectiveUnreadCount
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.overlay.OverlayGameStatusHudRefresh
import java.time.Instant

/** Shared unread math for team news/forum (app Team tab + overlay HUD). */
object TeamInboxUnread {
    fun isNewsItemUnread(
        item: TeamNewsListItemDto,
        prefs: UserSettingsPreferences,
        currentUserId: String,
    ): Boolean {
        if (currentUserId.isNotBlank() && item.authorUserId.trim() == currentUserId.trim()) {
            return false
        }
        val created = runCatching { Instant.parse(item.createdAt) }.getOrNull() ?: return false
        val lastSeenIso = prefs.getLastSeenTeamNewsCreatedAt()
        val lastSeen = lastSeenIso?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return lastSeen == null || created.isAfter(lastSeen)
    }

    fun countUnreadNews(
        items: List<TeamNewsListItemDto>,
        prefs: UserSettingsPreferences,
        currentUserId: String = "",
    ): Int = items.count { isNewsItemUnread(it, prefs, currentUserId) }

    fun sumForumUnread(
        topics: List<TeamForumTopicDto>,
        localReadByTopic: Map<String, String>,
    ): Int = topics.sumOf { topic ->
        OverlayGameStatusHudRefresh.effectiveForumTopicUnread(
            topic,
            localReadByTopic[topic.id],
        )
    }

    fun effectiveForumTopicUnread(
        topic: TeamForumTopicDto,
        localLastReadMessageId: String?,
    ): Int = effectiveUnreadCount(
        serverUnread = topic.unreadCount,
        lastReadMessageId = topic.lastReadMessageId,
        localLastReadMessageId = localLastReadMessageId,
    ).coerceAtLeast(0)
}
