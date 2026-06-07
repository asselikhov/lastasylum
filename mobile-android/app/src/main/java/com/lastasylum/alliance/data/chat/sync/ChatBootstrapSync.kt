package com.lastasylum.alliance.data.chat.sync

import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.ui.chat.ChatState
import com.lastasylum.alliance.ui.chat.capNewestFirst
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Initial rooms load + selected room open after splash/overlay. */
class ChatBootstrapSync(
    private val bootstrapMutex: Mutex,
    private val roomsListSync: ChatRoomsListSync,
    private val host: Host,
) {
    interface Host {
        fun stateSnapshot(): ChatState
        fun updateState(transform: (ChatState) -> ChatState)

        fun overlayRaidAlreadyReady(rooms: List<ChatRoomDto>): Boolean
        fun overlayHubAlreadyReady(rooms: List<ChatRoomDto>): Boolean
        fun recomputeRoomUnreadBadges()
        fun scheduleSyncRoomsFromServer(reconfirmVisibleRoom: Boolean)
        fun refreshMessagesInBackground(roomId: String, force: Boolean)

        suspend fun resolveRoomsForBootstrap(
            preferAllianceHubRoom: Boolean,
            preferOverlayRaidRoom: Boolean,
        ): Result<List<ChatRoomDto>>

        fun fallbackRoomsOnBootstrapError(error: Throwable): List<ChatRoomDto>?
        fun applyBootstrapEmptyRoomsError(message: String)
        fun applyBootstrapError(message: String)
        fun bootstrapErrorMessage(error: Throwable): String

        suspend fun applyRoomsFromServer(serverRooms: List<ChatRoomDto>): List<ChatRoomDto>
        fun syncRaidRoomPreference(rooms: List<ChatRoomDto>)
        fun syncOverlayAllianceHubBadge(rooms: List<ChatRoomDto>)
        fun schedulePersistChatSnapshot()

        fun resolveStartupRoomId(
            rooms: List<ChatRoomDto>,
            preferOverlayRaidRoom: Boolean,
        ): String
        fun setSelectedRoomPref(roomId: String)
        suspend fun reconcileStaleServerUnread(
            merged: List<ChatRoomDto>,
            raw: List<ChatRoomDto>,
        )

        fun roomMessageCache(roomId: String): ChatRoomMessageCache?
        fun seedRoomMessageCache(roomId: String, cache: ChatRoomMessageCache)

        suspend fun openRoom(
            roomId: String,
            rooms: List<ChatRoomDto>,
            hadCachedMessages: Boolean,
            messagesAlreadyInState: Boolean = false,
            deferNetworkMessages: Boolean = false,
        )

        fun setLaunchWarmupNeedsMessages(needs: Boolean)
    }

    suspend fun bootstrap(
        preferAllianceHubRoom: Boolean = false,
        preferOverlayRaidRoom: Boolean = false,
        force: Boolean = false,
        deferNetworkMessages: Boolean = false,
    ) {
        bootstrapMutex.withLock {
            if (!force) {
                if (preferOverlayRaidRoom && host.overlayRaidAlreadyReady(host.stateSnapshot().rooms)) {
                    host.recomputeRoomUnreadBadges()
                    host.scheduleSyncRoomsFromServer(reconfirmVisibleRoom = false)
                    host.stateSnapshot().selectedRoomId?.let { rid ->
                        host.refreshMessagesInBackground(rid, force = true)
                    }
                    return
                }
                if (preferAllianceHubRoom && host.overlayHubAlreadyReady(host.stateSnapshot().rooms)) {
                    host.recomputeRoomUnreadBadges()
                    host.scheduleSyncRoomsFromServer(reconfirmVisibleRoom = false)
                    return
                }
            }
        }
        host.updateState { st ->
            st.copy(
                isRoomsLoading = st.rooms.isEmpty() && st.messages.isEmpty(),
                error = null,
            )
        }
        val roomsResult = host.resolveRoomsForBootstrap(
            preferAllianceHubRoom = preferAllianceHubRoom,
            preferOverlayRaidRoom = preferOverlayRaidRoom,
        )
        val roomsRaw = roomsResult.getOrElse { e ->
            val fallback = host.fallbackRoomsOnBootstrapError(e)
            if (!fallback.isNullOrEmpty()) {
                fallback
            } else {
                host.applyBootstrapError(host.bootstrapErrorMessage(e))
                return
            }
        }
        val rooms = host.applyRoomsFromServer(roomsRaw)
        if (roomsResult.isSuccess) {
            ChatSessionCache.update(rooms)
            host.schedulePersistChatSnapshot()
        }
        if (rooms.isEmpty()) {
            host.applyBootstrapEmptyRoomsError("no_rooms")
            return
        }
        host.syncRaidRoomPreference(rooms)
        host.syncOverlayAllianceHubBadge(rooms)
        val selected = host.resolveStartupRoomId(
            rooms = rooms,
            preferOverlayRaidRoom = preferOverlayRaidRoom,
        )
        host.setSelectedRoomPref(selected)
        host.updateState { st ->
            st.copy(
                rooms = if (st.rooms.isEmpty()) rooms else st.rooms,
                selectedRoomId = selected,
                isRoomsLoading = false,
            )
        }
        host.reconcileStaleServerUnread(rooms, roomsRaw)
        val cachedOverlayMessages = ChatSessionCache.getFreshMessages(selected)
            ?: host.roomMessageCache(selected)?.messages
        if (!cachedOverlayMessages.isNullOrEmpty()) {
            val capped = capNewestFirst(cachedOverlayMessages, CHAT_PAGE_SIZE)
            host.seedRoomMessageCache(
                selected,
                ChatRoomMessageCache(
                    messages = capped,
                    hasMoreOlder = cachedOverlayMessages.size >= CHAT_PAGE_SIZE,
                ),
            )
            host.openRoom(
                selected,
                rooms,
                hadCachedMessages = true,
                deferNetworkMessages = deferNetworkMessages,
            )
        } else {
            host.openRoom(
                selected,
                rooms,
                hadCachedMessages = false,
                deferNetworkMessages = deferNetworkMessages,
            )
        }
    }
}
