package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatAllianceIds
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.data.teams.TeamNewsListItemDto
import com.lastasylum.alliance.data.users.TeamMemberDto
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.util.isOverlayIngameNow
import java.time.Instant

internal object OverlayGameStatusHudRefresh {
    suspend fun load(context: android.content.Context): OverlayGameStatusHudState {
        val container = AppContainer.from(context)
        val usersRepository = container.usersRepository
        val chatRepository = container.chatRepository
        val teamsRepository = container.teamsRepository
        val prefs = container.userSettingsPreferences

        val allianceUnread = chatRepository.listRooms()
            .getOrNull()
            ?.let(::allianceHubUnread)
            ?: 0

        val teamId = usersRepository.getMyProfile().getOrNull()?.playerTeamId?.trim().orEmpty()
        val newsUnread = if (teamId.isEmpty()) {
            0
        } else {
            teamsRepository.listTeamNews(teamId, cursor = null, limit = 40)
                .getOrNull()
                ?.items
                ?.let { countUnreadNews(it, prefs) }
                ?: 0
        }

        val forumUnread = if (teamId.isEmpty()) {
            0
        } else {
            teamsRepository.listForumTopics(teamId)
                .getOrNull()
                ?.sumOf { topic -> topic.unreadCount.coerceAtLeast(0) }
                ?: 0
        }

        return OverlayGameStatusHudState(
            allianceChatUnread = allianceUnread,
            teamNewsUnread = newsUnread,
            forumUnread = forumUnread,
        )
    }

    suspend fun loadIngameOverlayCount(context: android.content.Context): Int {
        val usersRepository = AppContainer.from(context).usersRepository
        return usersRepository.listMembers(allianceCode = null, q = null, skip = 0, limit = 300)
            .getOrNull()
            ?.let { filterIngameOverlayMembers(it).size }
            ?: 0
    }

    fun allianceHubRoom(rooms: List<ChatRoomDto>): ChatRoomDto? =
        rooms.firstOrNull { room ->
            room.sortOrder == 1 &&
                !room.allianceId.isNullOrBlank() &&
                room.allianceId != ChatAllianceIds.GLOBAL
        }

    fun allianceHubUnread(rooms: List<ChatRoomDto>): Int =
        allianceHubRoom(rooms)?.unreadCount?.coerceAtLeast(0) ?: 0

    fun filterIngameOverlayMembers(members: List<TeamMemberDto>): List<TeamMemberDto> =
        members.filter { m ->
            m.membershipStatus == "active" &&
                isOverlayIngameNow(m.presenceStatus, m.lastPresenceAt)
        }

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
        }
    }
}
