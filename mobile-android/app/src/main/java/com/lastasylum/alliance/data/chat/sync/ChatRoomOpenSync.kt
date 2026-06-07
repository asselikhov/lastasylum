package com.lastasylum.alliance.data.chat.sync

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.data.chat.store.ChatRoomStoreBindings
import com.lastasylum.alliance.ui.chat.ChatMessagesListDerived
import com.lastasylum.alliance.ui.chat.ChatState
import com.lastasylum.alliance.ui.chat.filterMessagesForRoom
import com.lastasylum.alliance.ui.chat.rebuildMessageIdIndex

/** Open/select room: cache hydrate, realtime subscribe, first page fetch. */
class ChatRoomOpenSync(
    private val repository: ChatRepository,
    private val syncEngine: ChatSyncEngine,
    private val roomStoreBindings: ChatRoomStoreBindings,
    private val pagingSync: ChatRoomPagingSync,
    private val rehydrateSync: ChatRehydrateSync,
    private val host: Host,
) {
    interface Host {
        val currentUserId: String

        fun isActiveSelectedRoom(roomId: String): Boolean
        fun stateSnapshot(): ChatState
        fun updateState(transform: (ChatState) -> ChatState)
        fun setState(state: ChatState)

        fun clearTypingForRoomOpen()
        fun setOtherReadUpto(roomId: String)
        suspend fun hydratePeerReadCursor(roomId: String, force: Boolean = false)
        fun isAllianceRaidRoom(roomId: String): Boolean
        fun isGlobalChatRoom(roomId: String, rooms: List<ChatRoomDto>): Boolean
        suspend fun loadTeamProfileGate(
            isGlobalRoom: Boolean,
            hadCachedMessages: Boolean,
            messagesAlreadyInState: Boolean,
        ): Boolean

        fun clearUnreadForRoomIfViewing(
            rooms: List<ChatRoomDto>,
            roomId: String,
            treatAsViewing: Boolean,
        ): List<ChatRoomDto>

        fun roomMessageCache(roomId: String): ChatRoomMessageCache?
        fun updateRoomMessageCache(roomId: String, cache: ChatRoomMessageCache)
        fun messagesWithoutLocallyRemoved(messages: List<ChatMessage>): List<ChatMessage>
        fun filterMessagesForRoom(messages: List<ChatMessage>, roomId: String): List<ChatMessage>
        fun capMessagesForMemory(messages: List<ChatMessage>): List<ChatMessage>
        fun knownMessageIds(): MutableSet<String>
        fun messageIdIndex(): MutableMap<String, Int>

        fun setListDerivedEmpty()
        fun publishMessagesDerived(messages: List<ChatMessage>)
        fun shouldAutoMarkReadSelectedRoom(): Boolean
        fun markRoomReadUpTo(roomId: String, messageId: String)
        fun schedulePersistChatSnapshot()
        fun setLaunchWarmupNeedsMessages(needs: Boolean)

        fun connectRealtime(rooms: List<ChatRoomDto>)
        fun applyOpenRoomLoadingState(
            roomId: String,
            rooms: List<ChatRoomDto>,
            hasTeam: Boolean,
        )
        fun applyOpenRoomCachedState(
            roomId: String,
            rooms: List<ChatRoomDto>,
            hasTeam: Boolean,
            filteredCache: List<ChatMessage>,
            hasMoreOlder: Boolean,
            messagesAlreadyInState: Boolean,
        )
        fun applyOpenRoomLoadError(message: String)
        fun loadErrorString(throwable: Throwable): String
    }

    suspend fun openRoom(
        roomId: String,
        rooms: List<ChatRoomDto>,
        hadCachedMessages: Boolean = false,
        messagesAlreadyInState: Boolean = false,
        deferNetworkMessages: Boolean = false,
    ) {
        val rid = roomId.trim()
        if (rid.isEmpty()) return
        if (!host.isActiveSelectedRoom(rid)) return
        roomStoreBindings.bindSelectedRoom(rid)
        host.hydratePeerReadCursor(rid, force = true)
        if (host.isAllianceRaidRoom(rid)) {
            rehydrateSync.rehydrateSelectedRoomMessagesFromCache()
        }
        host.clearTypingForRoomOpen()
        if (!messagesAlreadyInState) {
            host.knownMessageIds().clear()
            host.messageIdIndex().clear()
        }
        host.updateState { st ->
            if (!host.isActiveSelectedRoom(rid)) st
            else st.copy(
                rooms = host.clearUnreadForRoomIfViewing(
                    st.rooms.ifEmpty { rooms },
                    rid,
                    treatAsViewing = true,
                ),
            )
        }
        if (!host.isActiveSelectedRoom(rid)) return

        val storeSnapshot = if (host.currentUserId.isNotBlank()) {
            syncEngine.loadRoomSnapshotFromStore(host.currentUserId, rid)
        } else {
            null
        }
        val roomKnownLocally = storeSnapshot != null || rooms.any { it.id == rid }
        var hadCachedMessagesLocal = hadCachedMessages || storeSnapshot != null || roomKnownLocally

        if (storeSnapshot != null) {
            if (storeSnapshot.messages.isNotEmpty()) {
                val filteredStore = host.messagesWithoutLocallyRemoved(
                    host.filterMessagesForRoom(storeSnapshot.messages, rid),
                )
                if (filteredStore.isNotEmpty()) {
                    host.updateRoomMessageCache(
                        rid,
                        ChatRoomMessageCache(
                            messages = host.capMessagesForMemory(filteredStore),
                            hasMoreOlder = storeSnapshot.hasMoreOlder,
                        ),
                    )
                    ChatSessionCache.updateMessages(rid, host.roomMessageCache(rid)!!.messages)
                }
            } else {
                host.updateRoomMessageCache(
                    rid,
                    ChatRoomMessageCache(messages = emptyList(), hasMoreOlder = storeSnapshot.hasMoreOlder),
                )
            }
        }

        val isGlobalRoom = host.isGlobalChatRoom(rid, rooms)
        val hasTeam = host.loadTeamProfileGate(
            isGlobalRoom = isGlobalRoom,
            hadCachedMessages = hadCachedMessagesLocal,
            messagesAlreadyInState = messagesAlreadyInState,
        )
        if (!hadCachedMessagesLocal) {
            if (!host.isActiveSelectedRoom(rid)) return
            host.applyOpenRoomLoadingState(rid, rooms, hasTeam)
        } else {
            if (!host.isActiveSelectedRoom(rid)) return
            host.setOtherReadUpto(rid)
            host.updateState { st ->
                st.copy(
                    isRoomsLoading = false,
                    rooms = host.clearUnreadForRoomIfViewing(rooms, rid, treatAsViewing = true),
                    selectedRoomId = rid,
                    hasTeamProfileForGlobalChat = hasTeam,
                )
            }
        }
        host.connectRealtime(rooms)

        val cached = host.roomMessageCache(rid)
        if (hadCachedMessagesLocal && cached != null) {
            if (cached.messages.isNotEmpty()) {
                rehydrateSync.rehydrateRoomMessagesFromCache(rid)
                if (!host.isActiveSelectedRoom(rid)) return
                val filteredCache = host.messagesWithoutLocallyRemoved(
                    host.filterMessagesForRoom(host.roomMessageCache(rid)?.messages.orEmpty(), rid),
                )
                if (host.roomMessageCache(rid)?.messages?.size != filteredCache.size) {
                    host.updateRoomMessageCache(
                        rid,
                        cached.copy(messages = host.capMessagesForMemory(filteredCache)),
                    )
                    ChatSessionCache.updateMessages(rid, host.roomMessageCache(rid)!!.messages)
                }
                host.applyOpenRoomCachedState(
                    roomId = rid,
                    rooms = rooms,
                    hasTeam = hasTeam,
                    filteredCache = filteredCache,
                    hasMoreOlder = cached.hasMoreOlder,
                    messagesAlreadyInState = messagesAlreadyInState,
                )
                if (host.shouldAutoMarkReadSelectedRoom()) {
                    host.stateSnapshot().messages.firstOrNull()?._id?.let { newestId ->
                        host.markRoomReadUpTo(rid, newestId)
                    }
                }
                pagingSync.refreshMessagesInBackground(rid, force = true)
                host.schedulePersistChatSnapshot()
                return
            }
            // Known empty room: show empty state immediately, refresh in background.
            host.applyOpenRoomCachedState(
                roomId = rid,
                rooms = rooms,
                hasTeam = hasTeam,
                filteredCache = emptyList(),
                hasMoreOlder = cached.hasMoreOlder,
                messagesAlreadyInState = messagesAlreadyInState,
            )
            host.setListDerivedEmpty()
            pagingSync.refreshMessagesInBackground(rid, force = true)
            host.schedulePersistChatSnapshot()
            return
        }
        if (deferNetworkMessages) {
            host.setLaunchWarmupNeedsMessages(true)
            pagingSync.refreshMessagesInBackground(rid, force = true)
            host.schedulePersistChatSnapshot()
            return
        }
        if (roomKnownLocally) {
            host.applyOpenRoomCachedState(
                roomId = rid,
                rooms = rooms,
                hasTeam = hasTeam,
                filteredCache = emptyList(),
                hasMoreOlder = true,
                messagesAlreadyInState = messagesAlreadyInState,
            )
            host.setListDerivedEmpty()
            pagingSync.refreshMessagesInBackground(rid, force = true)
            host.schedulePersistChatSnapshot()
            return
        }
        repository.loadRecentMessages(rid, beforeMessageId = null, limit = CHAT_INITIAL_PAGE_SIZE)
            .onSuccess { loaded ->
                pagingSync.applyLoadedMessagePage(
                    roomId = rid,
                    loaded = loaded,
                    pageSizeForHasMore = CHAT_INITIAL_PAGE_SIZE,
                )
            }
            .onFailure { e ->
                if (!hadCachedMessagesLocal && host.isActiveSelectedRoom(rid)) {
                    host.applyOpenRoomLoadError(host.loadErrorString(e))
                }
            }
    }
}
