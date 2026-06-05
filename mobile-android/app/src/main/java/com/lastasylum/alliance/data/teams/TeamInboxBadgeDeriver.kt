package com.lastasylum.alliance.data.teams

import com.lastasylum.alliance.data.InboxUnreadReconciler
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.displayedUnreadCount
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.overlay.OverlayGameStatusHudRefresh

/** Single source for team inbox badge counts (overlay HUD, Team tab, mark-read refresh). */
object TeamInboxBadgeDeriver {
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
    ): Int {
        val topics = teamsRepository.listForumTopics(teamId).getOrNull() ?: return 0
        InboxUnreadReconciler.hydrateForumPrefsFromTopics(forumPrefs, teamId, topics)
        val localRead = forumPrefs.loadAllLastReadMessageIds(teamId)
        return computeForumUnread(topics, localRead)
    }
}
