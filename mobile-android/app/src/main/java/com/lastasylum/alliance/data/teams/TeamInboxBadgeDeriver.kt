package com.lastasylum.alliance.data.teams

import com.lastasylum.alliance.data.InboxUnreadReconciler
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.displayedUnreadCount
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.overlay.OverlayGameStatusHudRefresh

/** Effective + raw server forum unread for badge merge (local cursor suppression). */
data class ForumUnreadCounts(
    val effective: Int,
    val rawServer: Int,
)

/** Single source for team inbox badge counts (overlay HUD, Team tab, mark-read refresh). */
object TeamInboxBadgeDeriver {
    /** Client compute wins when topics are available; API is fallback only. */
    fun resolveForumUnread(
        clientUnread: Int?,
        apiUnread: Int?,
    ): Int = clientUnread ?: apiUnread?.coerceAtLeast(0) ?: 0

    fun computeForumRawUnread(topics: List<TeamForumTopicDto>): Int =
        topics.sumOf { it.unreadCount.coerceAtLeast(0) }

    fun computeForumUnreadCounts(
        topics: List<TeamForumTopicDto>,
        localReadByTopic: Map<String, String>,
    ): ForumUnreadCounts = ForumUnreadCounts(
        effective = computeForumUnread(topics, localReadByTopic),
        rawServer = computeForumRawUnread(topics),
    )

    fun mergeForDisplay(
        effectiveUnread: Int,
        previouslyDisplayed: Int,
        rawServerUnread: Int = effectiveUnread,
        optimisticFloor: Int = 0,
    ): Int = displayedUnreadCount(
        effectiveUnread = effectiveUnread,
        previouslyDisplayed = previouslyDisplayed,
        rawServerUnread = rawServerUnread,
        optimisticFloor = optimisticFloor,
    )

    fun computeHubChatUnread(
        rooms: List<ChatRoomDto>,
        localReadByRoom: Map<String, String>,
    ): Int = OverlayGameStatusHudRefresh.allianceHubUnread(rooms, localReadByRoom)

    fun computeNewsUnread(
        items: List<TeamNewsListItemDto>,
        prefs: UserSettingsPreferences,
        currentUserId: String,
    ): Int = TeamInboxUnread.countUnreadNews(items, prefs, currentUserId)

    fun computeForumUnread(
        topics: List<TeamForumTopicDto>,
        localReadByTopic: Map<String, String>,
    ): Int = TeamInboxUnread.sumForumUnread(topics, localReadByTopic)

    suspend fun computeForumUnreadFromRepository(
        teamsRepository: TeamsRepository,
        forumPrefs: TeamForumPreferences,
        teamId: String,
    ): Int = computeForumUnreadCountsFromRepository(
        teamsRepository = teamsRepository,
        forumPrefs = forumPrefs,
        teamId = teamId,
    ).effective

    suspend fun computeForumUnreadCountsFromRepository(
        teamsRepository: TeamsRepository,
        forumPrefs: TeamForumPreferences,
        teamId: String,
    ): ForumUnreadCounts {
        val topics = teamsRepository.listForumTopics(teamId).getOrNull() ?: return ForumUnreadCounts(0, 0)
        InboxUnreadReconciler.hydrateForumPrefsFromTopics(forumPrefs, teamId, topics)
        val localRead = forumPrefs.loadAllLastReadMessageIds(teamId)
        return computeForumUnreadCounts(topics, localRead)
    }
}
