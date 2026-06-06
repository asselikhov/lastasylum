package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.teams.TeamForumTopicDto

sealed class OverlayHudBadgeEvent {
    data class HubUnread(val displayed: Int) : OverlayHudBadgeEvent()

    data class NewsUnread(val effective: Int, val useAuthoritative: Boolean = true) : OverlayHudBadgeEvent()

    data class ForumUnread(
        val effective: Int,
        val rawServer: Int = effective,
        val useAuthoritative: Boolean = true,
    ) : OverlayHudBadgeEvent()

    data class FullRefreshResult(
        val allianceChatUnread: Int,
        val teamNewsUnread: Int,
        val forumUnread: Int,
        val appUpdateDownloadUrl: String? = null,
    ) : OverlayHudBadgeEvent()

    data class SeedFromLocal(
        val allianceChatUnread: Int,
        val teamNewsUnread: Int,
        val forumUnread: Int,
    ) : OverlayHudBadgeEvent()

    data class AppUpdateUrl(val url: String?) : OverlayHudBadgeEvent()

    data object ClearHub : OverlayHudBadgeEvent()

    data class ForumFromTopics(val topics: List<TeamForumTopicDto>, val teamId: String) : OverlayHudBadgeEvent()
}
