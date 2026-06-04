package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatAllianceIds
import com.lastasylum.alliance.data.chat.ChatHubRoomSync
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.ReadCursorSession
import com.lastasylum.alliance.data.InboxUnreadReconciler
import com.lastasylum.alliance.data.effectiveUnreadCount
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamInboxUnread
import com.lastasylum.alliance.data.teams.TeamNewsListItemDto
import com.lastasylum.alliance.data.users.TeamMemberDto
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.util.isOverlayIngameNow
import java.time.Instant

internal object OverlayGameStatusHudRefresh {
    private const val NEWS_FORUM_CACHE_TTL_MS = 180_000L

    @Volatile
    private var cachedBadgeTeamId: String? = null

    @Volatile
    private var cachedNewsUnread: Int = 0

    @Volatile
    private var cachedForumUnread: Int = 0

    @Volatile
    private var cachedBadgeAtMs: Long = 0L

    fun invalidateNewsForumCache() {
        cachedBadgeAtMs = 0L
    }

    fun invalidateNewsCache() = invalidateNewsForumCache()

    fun invalidateForumCache() = invalidateNewsForumCache()

    /**
     * @param preloadedRooms when non-null, skips [ChatRepository.listRooms] inside load.
     * @param refreshNewsForum when false, reuses news/forum counts for [NEWS_FORUM_CACHE_TTL_MS].
     */
    suspend fun load(
        context: android.content.Context,
        preloadedRooms: List<ChatRoomDto>? = null,
        refreshNewsForum: Boolean = true,
    ): OverlayGameStatusHudState {
        val container = AppContainer.from(context)
        val profile = container.usersRepository.resolveMyProfilePreferCache()
        val profileUserId = profile?.id?.trim().orEmpty()
        if (profileUserId.isNotEmpty()) {
            ReadCursorSession.bind(
                container.chatRoomPreferences,
                container.teamForumPreferences,
                container.userSettingsPreferences,
                profileUserId,
            )
        }
        val chatRepository = container.chatRepository
        val teamsRepository = container.teamsRepository
        val prefs = container.userSettingsPreferences

        val rooms = preloadedRooms
            ?: chatRepository.listRooms().getOrNull()
            ?: emptyList()
        val localChatRead = container.chatRoomPreferences.loadAllLastReadMessageIds()
        val allianceUnread = allianceHubUnread(rooms, localChatRead)

        val teamId = profile?.playerTeamId?.trim().orEmpty()
        val now = System.currentTimeMillis()
        val cacheFresh = !refreshNewsForum &&
            teamId.isNotEmpty() &&
            teamId == cachedBadgeTeamId &&
            now - cachedBadgeAtMs < NEWS_FORUM_CACHE_TTL_MS

        val newsUnread: Int
        val forumUnread: Int
        if (cacheFresh) {
            newsUnread = cachedNewsUnread
            forumUnread = cachedForumUnread
        } else if (teamId.isEmpty()) {
            newsUnread = 0
            forumUnread = 0
        } else {
            val newsAfter = prefs.getLastSeenTeamNewsCreatedAt()
            val badges = teamsRepository.getTeamInboxBadges(teamId, newsAfter).getOrNull()
            newsUnread = badges?.newsUnread?.coerceAtLeast(0)
                ?: teamsRepository.listTeamNews(teamId, cursor = null, limit = 40)
                    .getOrNull()
                    ?.items
                    ?.let { TeamInboxUnread.countUnreadNews(it, prefs, profileUserId) }
                    ?: 0
            val forumPrefs = container.teamForumPreferences
            val topics = teamsRepository.listForumTopics(teamId)
                .getOrNull()
            topics?.let { InboxUnreadReconciler.hydrateForumPrefsFromTopics(forumPrefs, teamId, it) }
            val localRead = forumPrefs.loadAllLastReadMessageIds(teamId)
            forumUnread = topics
                ?.let { TeamInboxUnread.sumForumUnread(it, localRead) }
                ?: badges?.forumUnread?.coerceAtLeast(0)
                ?: 0
            cachedBadgeTeamId = teamId
            cachedNewsUnread = newsUnread
            cachedForumUnread = forumUnread
            cachedBadgeAtMs = now
        }

        return OverlayGameStatusHudState(
            allianceChatUnread = allianceUnread,
            teamNewsUnread = newsUnread,
            forumUnread = forumUnread,
        )
    }

    suspend fun loadTeamJoinRequestCount(context: android.content.Context): Int {
        val container = AppContainer.from(context)
        val profile = container.usersRepository.resolveMyProfilePreferCache() ?: return 0
        if (!profile.isPlayerTeamLeader) return 0
        val fromProfile = profile.pendingPlayerTeamJoinRequests.coerceAtLeast(0)
        if (fromProfile > 0) return fromProfile
        return container.teamsRepository.listPendingJoinRequests()
            .getOrNull()
            ?.size
            ?: 0
    }

    fun effectiveForumTopicUnread(
        topic: TeamForumTopicDto,
        localLastReadMessageId: String?,
    ): Int = effectiveUnreadCount(
        serverUnread = topic.unreadCount,
        lastReadMessageId = topic.lastReadMessageId,
        localLastReadMessageId = localLastReadMessageId,
    ).coerceAtLeast(0)

    fun allianceHubRoom(rooms: List<ChatRoomDto>): ChatRoomDto? =
        ChatHubRoomSync.allianceHubRoom(rooms)

    fun allianceHubUnread(
        rooms: List<ChatRoomDto>,
        localReadByRoom: Map<String, String> = emptyMap(),
    ): Int {
        val hub = allianceHubRoom(rooms) ?: return 0
        return effectiveUnreadCount(
            serverUnread = hub.unreadCount,
            lastReadMessageId = hub.lastReadMessageId,
            localLastReadMessageId = localReadByRoom[hub.id],
        ).coerceAtLeast(0)
    }

    /** Server [ChatRoomDto.unreadCount] for hub before local cursor suppression (overlay badge merge). */
    fun allianceHubRawUnread(rooms: List<ChatRoomDto>): Int =
        allianceHubRoom(rooms)?.unreadCount?.coerceAtLeast(0) ?: 0

    fun filterIngameOverlayMembers(members: List<TeamMemberDto>): List<TeamMemberDto> =
        members.filter { m ->
            m.membershipStatus == "active" &&
                isOverlayIngameNow(m.presenceStatus, m.lastPresenceAt)
        }

    fun filterTeamIngameOverlayMembers(members: List<PlayerTeamMemberDto>): List<PlayerTeamMemberDto> =
        members.filter { m ->
            isOverlayIngameNow(m.presenceStatus, m.lastPresenceAt)
        }

    suspend fun countTeamIngameOverlayMembers(
        usersRepository: UsersRepository,
        teamsRepository: TeamsRepository,
    ): Int {
        val teamId = usersRepository.resolveMyProfilePreferCache()?.playerTeamId?.trim().orEmpty()
        if (teamId.isEmpty()) return 0
        return OverlayTeamPresenceCache.ingameCount(teamId, teamsRepository)
    }

    fun countUnreadNews(
        items: List<TeamNewsListItemDto>,
        prefs: UserSettingsPreferences,
        currentUserId: String = "",
    ): Int = TeamInboxUnread.countUnreadNews(items, prefs, currentUserId)

    /** Advance last-seen cursor when the user opens a news post (not on list load alone). */
    fun markTeamNewsSeenAt(createdAt: String?, prefs: UserSettingsPreferences) {
        val iso = createdAt?.trim().orEmpty()
        if (iso.isBlank()) return
        val incoming = runCatching { Instant.parse(iso) }.getOrNull() ?: return
        val prevIso = prefs.getLastSeenTeamNewsCreatedAt()
        val prev = prevIso?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (prev == null || incoming.isAfter(prev)) {
            prefs.setLastSeenTeamNewsCreatedAt(iso)
            invalidateNewsCache()
        }
    }
}
