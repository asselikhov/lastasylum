package com.lastasylum.alliance.overlay

import android.content.Context
import com.lastasylum.alliance.data.InboxUnreadReconciler
import com.lastasylum.alliance.data.ReadCursorSession
import com.lastasylum.alliance.data.chat.ChatHubRoomSync
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.data.teams.TeamInboxUnread
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.prefetchTeamLaunchContent

data class OverlayColdHydrateResult(
    val seededContext: Boolean,
    val seededRooms: Boolean,
    val seededBadges: Boolean,
    val needsNetworkPrefetch: Boolean,
)

/** Seeds overlay RAM caches from [LaunchDiskCache] on game entry (offline-first). */
internal object OverlayColdStartHydrator {

    suspend fun hydrate(context: Context, userId: String): OverlayColdHydrateResult {
        val uid = userId.trim()
        if (uid.isEmpty()) {
            return OverlayColdHydrateResult(
                seededContext = false,
                seededRooms = false,
                seededBadges = false,
                needsNetworkPrefetch = false,
            )
        }
        val container = AppContainer.from(context)
        val disk = container.launchDiskCache

        ReadCursorSession.bind(
            container.chatRoomPreferences,
            container.teamForumPreferences,
            container.userSettingsPreferences,
            uid,
        )
        ReadCursorSession.syncAllInboxReadCursors(
            container.usersRepository,
            container.teamsRepository,
            container.chatRepository,
            container.chatRoomPreferences,
            container.teamForumPreferences,
            container.userSettingsPreferences,
        )

        var seededContext = false
        var seededRooms = false
        var seededBadges = false
        var hasAnyDisk = false

        val profile = disk.loadProfile(uid)
        val team = disk.loadTeam(uid)
        if (profile != null && team != null) {
            OverlayTeamContextCache.seedFromDisk(profile, team)
            seededContext = true
            hasAnyDisk = true
        }

        val rooms = disk.loadChatRooms(uid)
        if (!rooms.isNullOrEmpty()) {
            ChatSessionCache.update(rooms)
            ChatHubRoomSync.applyHubRoomPreference(rooms, container.chatRoomPreferences)
            seededRooms = true
            hasAnyDisk = true
            ChatHubRoomSync.allianceHubRoom(rooms)?.id?.let { hubId ->
                disk.loadRoomMessages(uid, hubId)?.messages?.let { messages ->
                    if (messages.isNotEmpty()) {
                        ChatSessionCache.updateMessages(hubId, messages)
                    }
                }
            }
        }

        val teamId = profile?.playerTeamId?.trim().orEmpty()
        if (teamId.isNotEmpty()) {
            val newsPage = disk.loadTeamNews(uid, teamId)
            val forumTopics = disk.loadForumTopics(uid, teamId)
            if (newsPage != null || !forumTopics.isNullOrEmpty()) {
                forumTopics?.let { topics ->
                    InboxUnreadReconciler.hydrateForumPrefsFromTopics(
                        container.teamForumPreferences,
                        teamId,
                        topics,
                    )
                }
                val newsUnread = newsPage?.items?.let { items ->
                    TeamInboxUnread.countUnreadNews(
                        items,
                        container.userSettingsPreferences,
                        uid,
                    )
                } ?: 0
                val localForumRead = container.teamForumPreferences.loadAllLastReadMessageIds(teamId)
                val forumUnread = forumTopics
                    ?.let { TeamInboxUnread.sumForumUnread(it, localForumRead) }
                    ?: 0
                OverlayGameStatusHudRefresh.seedBadgesFromDisk(teamId, newsUnread, forumUnread)
                seededBadges = true
                hasAnyDisk = true
            }
        }

        val needsNetworkPrefetch = !hasAnyDisk || profile == null || team == null
        return OverlayColdHydrateResult(
            seededContext = seededContext,
            seededRooms = seededRooms,
            seededBadges = seededBadges,
            needsNetworkPrefetch = needsNetworkPrefetch,
        )
    }

    suspend fun prefetchTeamContent(context: Context, userId: String) {
        val uid = userId.trim()
        if (uid.isEmpty()) return
        val container = AppContainer.from(context)
        val profile = container.launchDiskCache.loadProfile(uid)
            ?: container.usersRepository.getMyProfile().getOrNull()
            ?: return
        prefetchTeamLaunchContent(uid, profile, container)
    }
}
