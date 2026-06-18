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
    /** Prefer client effective count; trust API only when client still has unread. */
    fun resolveForumUnread(
        clientUnread: Int?,
        apiUnread: Int?,
    ): Int {
        val client = clientUnread?.coerceAtLeast(0)
        val api = apiUnread?.coerceAtLeast(0)
        return when {
            client == null -> api ?: 0
            api == null -> client
            client == 0 -> 0
            else -> maxOf(client, api)
        }
    }

    fun computeForumRawUnread(topics: List<TeamForumTopicDto>): Int =
        topics.sumOf { it.unreadCount.coerceAtLeast(0) }

    fun computeForumUnreadCounts(
        topics: List<TeamForumTopicDto>,
        localReadByTopic: Map<String, String>,
        optimisticFloorByTopic: Map<String, Int> = emptyMap(),
    ): ForumUnreadCounts = ForumUnreadCounts(
        effective = computeForumUnread(topics, localReadByTopic, optimisticFloorByTopic),
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
        teamId: String,
        currentUserId: String,
    ): Int = TeamInboxUnread.countUnreadNews(items, prefs, teamId, currentUserId)

    fun computeForumUnread(
        topics: List<TeamForumTopicDto>,
        localReadByTopic: Map<String, String>,
        optimisticFloorByTopic: Map<String, Int> = emptyMap(),
    ): Int = TeamInboxUnread.sumForumUnread(topics, localReadByTopic, optimisticFloorByTopic)

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
