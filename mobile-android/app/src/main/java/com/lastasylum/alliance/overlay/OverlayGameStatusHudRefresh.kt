package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatAllianceIds
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.effectiveUnreadCount
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamForumPreferences
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
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
        val usersRepository = container.usersRepository
        val chatRepository = container.chatRepository
        val teamsRepository = container.teamsRepository
        val prefs = container.userSettingsPreferences

        val rooms = preloadedRooms
            ?: chatRepository.listRooms().getOrNull()
            ?: emptyList()
        val localChatRead = container.chatRoomPreferences.loadAllLastReadMessageIds()
        val allianceUnread = allianceHubUnread(rooms, localChatRead)

        val teamId = usersRepository.getMyProfile().getOrNull()?.playerTeamId?.trim().orEmpty()
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
            newsUnread = teamsRepository.listTeamNews(teamId, cursor = null, limit = 40)
                .getOrNull()
                ?.items
                ?.let { countUnreadNews(it, prefs) }
                ?: 0
            val forumPrefs = TeamForumPreferences(context)
            val localRead = forumPrefs.loadAllLastReadMessageIds(teamId)
            forumUnread = teamsRepository.listForumTopics(teamId)
                .getOrNull()
                ?.sumOf { topic -> effectiveForumTopicUnread(topic, localRead[topic.id]) }
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
        val profile = container.usersRepository.getMyProfile().getOrNull() ?: return 0
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
        rooms.firstOrNull { room ->
            room.sortOrder == 1 &&
                !room.allianceId.isNullOrBlank() &&
                room.allianceId.startsWith("pt:") &&
                room.allianceId != ChatAllianceIds.GLOBAL &&
                !ChatAllianceIds.isServerScope(room.allianceId)
        }

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

    fun filterIngameOverlayMembers(members: List<TeamMemberDto>): List<TeamMemberDto> =
        members.filter { m ->
            m.membershipStatus == "active" &&
                isOverlayIngameNow(m.presenceStatus, m.lastPresenceAt)
        }

    fun filterTeamIngameOverlayMembers(members: List<PlayerTeamMemberDto>): List<PlayerTeamMemberDto> =
        members.filter { m ->
            isOverlayIngameNow(m.presenceStatus, m.lastPresenceAt)
        }.sortedBy { it.username.lowercase() }

    fun countUnreadNews(
        items: List<TeamNewsListItemDto>,
        prefs: UserSettingsPreferences,
    ): Int {
        val lastSeen = prefs.getLastSeenTeamNewsCreatedAt()
        val lastSeenInstant = lastSeen?.let { iso ->
            runCatching { Instant.parse(iso) }.getOrNull()
        }
        return items.count { item ->
            val created = runCatching { Instant.parse(item.createdAt) }.getOrNull()
                ?: return@count false
            lastSeenInstant == null || created.isAfter(lastSeenInstant)
        }
    }

    /** Mark all items in the fetched page as seen (newest createdAt wins). */
    fun markTeamNewsSeenFromItems(
        items: List<TeamNewsListItemDto>,
        prefs: UserSettingsPreferences,
    ) {
        val newest = items.maxByOrNull { item ->
            runCatching { Instant.parse(item.createdAt) }.getOrNull() ?: Instant.EPOCH
        }?.createdAt
        if (!newest.isNullOrBlank()) {
            prefs.setLastSeenTeamNewsCreatedAt(newest)
            invalidateNewsForumCache()
        }
    }
}
