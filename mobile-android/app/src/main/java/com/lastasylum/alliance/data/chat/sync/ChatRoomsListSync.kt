package com.lastasylum.alliance.data.chat.sync

import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatRoomsSessionCache
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.data.chat.ChatTeamRoomsMembership
import com.lastasylum.alliance.data.users.UsersRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** listRooms refresh for tab badges and overlay resume. */
class ChatRoomsListSync(
    private val scope: CoroutineScope,
    private val repository: ChatRepository,
    private val usersRepository: UsersRepository,
    private val chatRoomPreferences: com.lastasylum.alliance.data.chat.ChatRoomPreferences,
    private val host: Host,
) {
    interface Host {
        fun isChatTabActive(): Boolean
        fun overlayChatPanelVisible(): Boolean
        fun hasPendingUnreadReconcile(): Boolean
        fun stateRooms(): List<ChatRoomDto>

        suspend fun applyRoomsFromServer(serverRooms: List<ChatRoomDto>): List<ChatRoomDto>
        fun publishRooms(next: List<ChatRoomDto>)
        fun syncTabUnreadBadge(rooms: List<ChatRoomDto>)
        fun syncRaidRoomPreference(rooms: List<ChatRoomDto>)
        suspend fun reconcileStaleServerUnread(
            mergedRooms: List<ChatRoomDto>,
            rawServerRooms: List<ChatRoomDto>,
        )
        suspend fun reconfirmReadForVisibleRoom()
        fun syncOverlayAllianceHubBadge(rooms: List<ChatRoomDto>)
        fun reconnectRealtimeIfNeeded()
        fun setLastRoomsSyncedAtMs(atMs: Long)
        fun schedulePersistChatSnapshot()
        fun applyChatHistoryClearedFromServer()
    }

    fun syncRoomsFromServer(reconfirmVisibleRoom: Boolean = true) {
        if (!host.isChatTabActive() &&
            !host.overlayChatPanelVisible() &&
            !host.hasPendingUnreadReconcile()
        ) {
            return
        }
        scope.launch {
            if (!host.isChatTabActive() &&
                !host.overlayChatPanelVisible() &&
                !host.hasPendingUnreadReconcile()
            ) {
                return@launch
            }
            com.lastasylum.alliance.data.chat.ChatHistorySync.reconcileIfNeeded(
                repository = repository,
                chatRoomPreferences = chatRoomPreferences,
                onServerHistoryCleared = { host.applyChatHistoryClearedFromServer() },
            )
            repository.listRooms()
                .onSuccess { raw ->
                    val next = host.applyRoomsFromServer(raw)
                    host.syncRaidRoomPreference(next)
                    host.publishRooms(next)
                    host.syncTabUnreadBadge(next)
                    host.reconcileStaleServerUnread(next, raw)
                    if (reconfirmVisibleRoom) {
                        host.reconfirmReadForVisibleRoom()
                    }
                    host.syncOverlayAllianceHubBadge(next)
                    host.setLastRoomsSyncedAtMs(System.currentTimeMillis())
                    host.reconnectRealtimeIfNeeded()
                }
        }
    }

    suspend fun resolveRoomsForBootstrap(
        preferAllianceHubRoom: Boolean,
        preferOverlayRaidRoom: Boolean = false,
    ): Result<List<ChatRoomDto>> {
        val teamId = usersRepository.peekMyProfile()?.playerTeamId
            ?: usersRepository.resolveMyProfilePreferCache()?.playerTeamId
        ChatSessionCache.getFreshRooms()?.let { cached ->
            if (ChatTeamRoomsMembership.cacheMatchesProfile(cached, teamId)) {
                return Result.success(cached)
            }
            ChatRoomsSessionCache.invalidate()
        }
        if ((preferAllianceHubRoom || preferOverlayRaidRoom) && host.stateRooms().isNotEmpty()) {
            val current = host.stateRooms()
            if (ChatTeamRoomsMembership.cacheMatchesProfile(current, teamId)) {
                return Result.success(current)
            }
        }
        return repository.listRooms()
    }

    fun syncOverlayRoomsQuietly() {
        scope.launch {
            repository.listRooms()
                .onSuccess { raw ->
                    val next = host.applyRoomsFromServer(raw)
                    host.syncRaidRoomPreference(next)
                    ChatSessionCache.update(next)
                    host.publishRooms(next)
                    host.syncTabUnreadBadge(next)
                    host.syncOverlayAllianceHubBadge(next)
                    host.reconnectRealtimeIfNeeded()
                    host.schedulePersistChatSnapshot()
                }
        }
    }
}
