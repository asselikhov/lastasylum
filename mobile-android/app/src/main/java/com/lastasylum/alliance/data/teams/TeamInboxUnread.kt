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
        teamId: String,
        currentUserId: String,
    ): Boolean {
        if (currentUserId.isNotBlank() && item.authorUserId.trim() == currentUserId.trim()) {
            return false
        }
        val created = runCatching { Instant.parse(item.createdAt) }.getOrNull() ?: return false
        val lastSeenIso = prefs.getLastSeenTeamNewsCreatedAt(teamId)
        val lastSeen = lastSeenIso?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return lastSeen == null || created.isAfter(lastSeen)
    }

    fun countUnreadNews(
        items: List<TeamNewsListItemDto>,
        prefs: UserSettingsPreferences,
        teamId: String,
        currentUserId: String = "",
    ): Int = items.count { isNewsItemUnread(it, prefs, teamId, currentUserId) }

    /** Newest post first (same order as forum topic list / API `_id: -1`). */
    fun sortNewsFeedNewestFirst(items: List<TeamNewsListItemDto>): List<TeamNewsListItemDto> {
        if (items.size <= 1) return items
        val sorted = items.sortedWith(
            compareByDescending<TeamNewsListItemDto> { item ->
                runCatching { Instant.parse(item.createdAt.trim()) }.getOrNull() ?: Instant.EPOCH
            }.thenByDescending { it.id },
        )
        // Pagination append can overlap cursor boundaries — duplicate ids crash LazyColumn.
        val seen = HashSet<String>()
        return sorted.filter { item ->
            val id = item.id.trim()
            id.isEmpty() || seen.add(id)
        }
    }

    fun sumForumUnread(
        topics: List<TeamForumTopicDto>,
        localReadByTopic: Map<String, String>,
        optimisticFloorByTopic: Map<String, Int> = emptyMap(),
    ): Int = topics.sumOf { topic ->
        displayedForumTopicUnread(
            topic = topic,
            localLastReadMessageId = localReadByTopic[topic.id],
            optimisticFloor = optimisticFloorByTopic[topic.id] ?: 0,
        )
    }

    fun displayedForumTopicUnread(
        topic: TeamForumTopicDto,
        localLastReadMessageId: String?,
        optimisticFloor: Int = 0,
    ): Int {
        val effective = effectiveUnreadCount(
            serverUnread = topic.unreadCount,
            lastReadMessageId = topic.lastReadMessageId,
            localLastReadMessageId = localLastReadMessageId,
        )
        return com.lastasylum.alliance.data.displayedUnreadCount(
            effectiveUnread = effective,
            previouslyDisplayed = 0,
            rawServerUnread = topic.unreadCount.coerceAtLeast(0),
            optimisticFloor = optimisticFloor,
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
