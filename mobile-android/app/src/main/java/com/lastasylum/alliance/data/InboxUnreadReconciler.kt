package com.lastasylum.alliance.data

import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.data.teams.TeamForumPreferences
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamInboxUnread
import com.lastasylum.alliance.data.teams.TeamsRepository

/** Reinstall-safe repair for stale server unread counts and forum/chat read cursors. */
object InboxUnreadReconciler {
    suspend fun repairForumStaleUnread(
        teamsRepository: TeamsRepository,
        forumPrefs: TeamForumPreferences,
        teamId: String,
        topics: List<TeamForumTopicDto>,
    ) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        for (topic in topics) {
            if (topic.unreadCount <= 0) continue
            val local = forumPrefs.getLastReadMessageId(tid, topic.id)
            val effective = effectiveUnreadCount(
                serverUnread = topic.unreadCount,
                lastReadMessageId = topic.lastReadMessageId,
                localLastReadMessageId = local,
            )
            when {
                effective == 0 && !local.isNullOrBlank() -> {
                    teamsRepository.markForumTopicRead(tid, topic.id, local)
                }
                effective == 0 && !topic.lastReadMessageId.isNullOrBlank() -> {
                    val serverLast = topic.lastReadMessageId.trim()
                    teamsRepository.markForumTopicRead(tid, topic.id, serverLast)
                    mergeForumReadCursor(forumPrefs, tid, topic.id, serverLast)
                }
            }
        }
    }

    /** When local news cursor proves read but server badge is still stale, re-POST cursor. */
    suspend fun repairNewsStaleUnread(
        teamsRepository: TeamsRepository,
        userSettingsPreferences: UserSettingsPreferences,
        teamId: String,
        currentUserId: String,
        apiNewsUnread: Int,
    ) {
        if (apiNewsUnread <= 0) return
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        val localIso = userSettingsPreferences.getLastSeenTeamNewsCreatedAt(tid)?.trim().orEmpty()
        if (localIso.isEmpty()) return
        val clientUnread = teamsRepository.listTeamNews(tid, cursor = null, limit = 40)
            .getOrNull()
            ?.items
            ?.let { items ->
                TeamInboxUnread.countUnreadNews(
                    items = items,
                    prefs = userSettingsPreferences,
                    teamId = tid,
                    currentUserId = currentUserId,
                )
            } ?: return
        if (clientUnread > 0) return
        teamsRepository.advanceTeamNewsReadCursor(tid, localIso)
    }

    suspend fun repairChatStaleUnread(
        chatRepository: ChatRepository,
        chatRoomPreferences: ChatRoomPreferences,
    ) {
        val rooms = chatRepository.listRooms().getOrNull() ?: return
        repairChatStaleUnread(rooms, chatRepository, chatRoomPreferences)
    }

    suspend fun repairChatStaleUnread(
        rooms: List<ChatRoomDto>,
        chatRepository: ChatRepository,
        chatRoomPreferences: ChatRoomPreferences,
    ) {
        for (room in rooms) {
            if (room.unreadCount <= 0) continue
            val deviceLocal = chatRoomPreferences.getLastReadMessageId(room.id)
            val effective = effectiveUnreadCount(
                serverUnread = room.unreadCount,
                lastReadMessageId = room.lastReadMessageId,
                localLastReadMessageId = deviceLocal,
            )
            when {
                effective == 0 && !deviceLocal.isNullOrBlank() -> {
                    chatRepository.markRoomRead(room.id, deviceLocal)
                    mergeChatReadCursor(chatRoomPreferences, room.id, deviceLocal)
                }
                effective == 0 && !room.lastReadMessageId.isNullOrBlank() -> {
                    val serverLast = room.lastReadMessageId.trim()
                    chatRepository.markRoomRead(room.id, serverLast)
                    mergeChatReadCursor(chatRoomPreferences, room.id, serverLast)
                }
            }
        }
    }

    private fun mergeForumReadCursor(
        forumPrefs: TeamForumPreferences,
        teamId: String,
        topicId: String,
        messageId: String,
    ) {
        if (teamId.isBlank() || topicId.isBlank() || messageId.isBlank()) return
        val existing = forumPrefs.getLastReadMessageId(teamId, topicId)?.trim().orEmpty()
        if (existing.isBlank() || isObjectIdNewer(messageId, existing)) {
            forumPrefs.setLastReadMessageId(teamId, topicId, messageId)
        }
    }

    private fun mergeChatReadCursor(
        chatRoomPreferences: ChatRoomPreferences,
        roomId: String,
        messageId: String,
    ) {
        if (roomId.isBlank() || messageId.isBlank()) return
        val existing = chatRoomPreferences.getLastReadMessageId(roomId)?.trim().orEmpty()
        if (existing.isBlank() || isObjectIdNewer(messageId, existing)) {
            chatRoomPreferences.setLastReadMessageId(roomId, messageId)
        }
    }
}
