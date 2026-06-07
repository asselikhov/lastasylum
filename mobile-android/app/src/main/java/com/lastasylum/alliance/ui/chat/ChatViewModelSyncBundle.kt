package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.sync.applyOverlayLoadTimeoutPolicy
import com.lastasylum.alliance.data.chat.sync.ChatRoomMessageCache
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update

/** Sync coordinator hosts for [ChatViewModel]. */
internal class ChatViewModelSyncBundle(
    private val vm: ChatViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    repository: com.lastasylum.alliance.data.chat.ChatRepository,
    bootstrapMutex: kotlinx.coroutines.sync.Mutex,
) {
    val rehydrateSyncHost = object : com.lastasylum.alliance.data.chat.sync.ChatRehydrateSync.Host {
        override val messageMemoryCap: Int get() = vm.messageMemoryCap
        override val currentUserId: String get() = vm.currentUserIdInternal
        override fun stateSnapshot(): ChatState = vm.vmState.value
        override fun selectedRoomId(): String? = vm.vmState.value.selectedRoomId
        override fun isActiveSelectedRoom(roomId: String): Boolean = vm.isActiveSelectedRoom(roomId)
        override fun messagesWithoutLocallyRemoved(messages: List<ChatMessage>) =
            vm.messagesWithoutLocallyRemoved(messages)
        override fun filterMessagesForRoom(messages: List<ChatMessage>, roomId: String) =
            vm.filterMessagesForRoom(messages, roomId)
        override fun hiddenBeforeForRoom(roomId: String): String? = vm.hiddenBeforeForRoom(roomId)
        override fun locallyRemovedMessageIds(): Set<String> = vm.locallyRemovedMessageIds
        override fun knownMessageIds(): MutableSet<String> = vm.knownMessageIds
        override fun messageIdIndex(): MutableMap<String, Int> = vm.messageIdIndex
        override fun roomMessageCache(roomId: String): ChatRoomMessageCache? = vm.roomMessageCache[roomId]
        override fun updateRoomMessageCache(roomId: String, cache: ChatRoomMessageCache) {
            vm.roomMessageCache[roomId] = cache
        }
        override fun capMessagesForMemory(messages: List<ChatMessage>) = vm.capMessagesForMemory(messages)
        override fun applyRehydratedMessages(roomId: String, merged: List<ChatMessage>, hasMoreOlder: Boolean) {
            val filtered = vm.filterMessagesForRoom(merged, roomId)
            val safe = sanitizeMessagesForUiList(
                messages = filtered,
                currentUserId = vm.currentUserIdInternal,
                activeOutgoingPendingId = vm.outboxRoomSnapshot.newestPendingId,
            )
            vm.vmState.update {
                it.copy(
                    messages = safe,
                    selectedRoomId = roomId,
                    hasMoreOlder = hasMoreOlder,
                    isLoading = false,
                    newestMessageKey = safe.firstOrNull()?._id ?: it.newestMessageKey,
                )
            }
        }
        override fun publishMessagesDerived(messages: List<ChatMessage>) = vm.publishMessagesDerived(messages)
        override fun publishMessagesDerivedImmediate(messages: List<ChatMessage>) =
            vm.publishMessagesDerivedImmediate(messages)
    }

    val rehydrateSync = com.lastasylum.alliance.data.chat.sync.ChatRehydrateSync(rehydrateSyncHost)

    val pagingSyncHost = object : com.lastasylum.alliance.data.chat.sync.ChatRoomPagingSync.Host {
        override val currentUserId: String get() = vm.currentUserIdInternal
        override val messageMemoryCap: Int get() = vm.messageMemoryCap
        override fun stateSnapshot(): ChatState = vm.vmState.value
        override fun selectedRoomId(): String? = vm.vmState.value.selectedRoomId
        override fun overlayChatPanelVisible(): Boolean = vm.overlayChatPanelVisible
        override fun isAllianceRaidRoom(roomId: String): Boolean = vm.isAllianceRaidRoom(roomId)
        override fun messagesForRoomMerge(roomId: String) = vm.messagesForRoomMerge(roomId)
        override fun messagesWithoutLocallyRemoved(messages: List<ChatMessage>) =
            vm.messagesWithoutLocallyRemoved(messages)
        override fun filterMessagesForRoom(messages: List<ChatMessage>, roomId: String) =
            vm.filterMessagesForRoom(messages, roomId)
        override fun protectedSocketMessageIds(): Set<String> = vm.protectedSocketMessageIds()
        override fun mergeAnchorDropLogger(roomId: String) = vm.mergeAnchorDropLogger(roomId)
        override fun locallyRemovedMessageIds(): Set<String> = vm.locallyRemovedMessageIds
        override fun knownMessageIds(): MutableSet<String> = vm.knownMessageIds
        override fun messageIdIndex(): MutableMap<String, Int> = vm.messageIdIndex
        override fun capMessagesForMemory(messages: List<ChatMessage>) = vm.capMessagesForMemory(messages)
        override fun isActiveSelectedRoom(roomId: String): Boolean = vm.isActiveSelectedRoom(roomId)
        override fun shouldAutoMarkReadSelectedRoom(): Boolean = vm.shouldAutoMarkReadSelectedRoom()
        override fun resolvedLastReadForRoom(roomId: String): String? = vm.deviceLastReadMessageId(roomId)
        override fun updateRoomMessageCache(roomId: String, cache: ChatRoomMessageCache) {
            vm.roomMessageCache[roomId] = cache
        }
        override fun roomMessageCache(roomId: String): ChatRoomMessageCache? = vm.roomMessageCache[roomId]
        override fun lastBackgroundRefreshAtMs(roomId: String): Long = vm.lastBackgroundRefreshAtMs[roomId] ?: 0L
        override fun setLastBackgroundRefreshAtMs(roomId: String, atMs: Long) {
            vm.lastBackgroundRefreshAtMs[roomId] = atMs
        }
        override fun forceBackgroundRefreshAfterReconnect(): Boolean = vm.forceBackgroundRefreshAfterReconnect
        override fun setForceBackgroundRefreshAfterReconnect(value: Boolean) {
            vm.forceBackgroundRefreshAfterReconnect = value
        }
        override fun isRoomAuthoritativeEmpty(roomId: String): Boolean = vm.isRoomAuthoritativeEmpty(roomId)
        override fun clearRoomAuthoritativeEmpty(roomId: String) = vm.clearRoomAuthoritativeEmpty(roomId)
        override fun applyLoadedPageToUi(roomId: String, capped: List<ChatMessage>, hasMoreOlder: Boolean) {
            vm.vmState.value = vm.vmState.value.copy(
                isLoading = false,
                messages = capped,
                selectedRoomId = roomId,
                hasMoreOlder = hasMoreOlder,
                rooms = vm.clearUnreadForRoomIfViewing(vm.vmState.value.rooms, roomId, treatAsViewing = true),
            )
        }
        override fun applyMergedPageToUi(roomId: String, merged: List<ChatMessage>, hasMoreOlder: Boolean) {
            val filtered = vm.filterMessagesForRoom(merged, roomId)
            val safe = sanitizeMessagesForUiList(
                messages = filtered,
                currentUserId = vm.currentUserIdInternal,
                activeOutgoingPendingId = vm.outboxRoomSnapshot.newestPendingId,
            )
            vm.vmState.update {
                it.copy(messages = safe, hasMoreOlder = hasMoreOlder, newestMessageKey = safe.firstOrNull()?._id)
            }
        }
        override fun applyLoadingOlder(loading: Boolean) {
            vm.vmState.update { it.copy(isLoadingOlder = loading) }
        }
        override fun applyOlderPageToUi(messages: List<ChatMessage>) {
            vm.vmState.update { it.copy(messages = messages) }
        }
        override fun applyOlderPagingComplete(messages: List<ChatMessage>?, hasMoreOlder: Boolean) {
            vm.vmState.update {
                if (messages == null) it.copy(isLoadingOlder = false, hasMoreOlder = hasMoreOlder)
                else it.copy(messages = messages, isLoadingOlder = false, hasMoreOlder = hasMoreOlder)
            }
        }
        override fun applyOlderPagingError(message: String) {
            vm.vmState.update { it.copy(isLoadingOlder = false, transientNotice = message) }
        }
        override fun applyPagingError(errorMessage: String) {
            vm.vmState.update { it.copy(isLoading = false, error = errorMessage) }
        }
        override fun applyOverlayLoadTimeout(message: String) {
            vm.vmState.update { applyOverlayLoadTimeoutPolicy(it, message) }
        }
        override fun markRoomReadUpTo(roomId: String, messageId: String) {
            scope.launch { vm.markRoomReadUpTo(roomId, messageId) }
        }
        override fun schedulePersistChatSnapshot() = vm.schedulePersistChatSnapshot()
        override fun publishMessagesDerived(messages: List<ChatMessage>) = vm.publishMessagesDerived(messages)
        override fun publishMessagesDerivedImmediate(messages: List<ChatMessage>) =
            vm.publishMessagesDerivedImmediate(messages)
        override fun loadErrorString(throwable: Throwable) = throwable.toUserMessageRu(vm.res)
        override fun overlayTimeoutString(): String =
            vm.res.getString(com.lastasylum.alliance.R.string.overlay_panel_load_timeout)
    }

    val roomPagingSync = com.lastasylum.alliance.data.chat.sync.ChatRoomPagingSync(
        scope = scope,
        repository = repository,
        messageStore = vm.messageStore,
        host = pagingSyncHost,
    )

    val roomsListSyncHost = object : com.lastasylum.alliance.data.chat.sync.ChatRoomsListSync.Host {
        override fun isChatTabActive(): Boolean = vm.isChatTabActive
        override fun overlayChatPanelVisible(): Boolean = vm.overlayChatPanelVisible
        override fun hasPendingUnreadReconcile(): Boolean = vm.hasPendingUnreadReconcile()
        override fun stateRooms(): List<ChatRoomDto> = vm.vmState.value.rooms
        override suspend fun applyRoomsFromServer(serverRooms: List<ChatRoomDto>) =
            vm.applyRoomsFromServer(serverRooms)
        override fun publishRooms(next: List<ChatRoomDto>) = vm.publishRooms(next)
        override fun syncTabUnreadBadge(rooms: List<ChatRoomDto>) = vm.syncTabUnreadBadge(rooms)
        override fun syncRaidRoomPreference(rooms: List<ChatRoomDto>) = vm.syncRaidRoomPreference(rooms)
        override suspend fun reconcileStaleServerUnread(
            mergedRooms: List<ChatRoomDto>,
            rawServerRooms: List<ChatRoomDto>,
        ) = vm.reconcileStaleServerUnread(mergedRooms, rawServerRooms)
        override suspend fun reconfirmReadForVisibleRoom() = vm.reconfirmReadForVisibleRoom()
        override fun syncOverlayAllianceHubBadge(rooms: List<ChatRoomDto>) =
            vm.syncOverlayAllianceHubBadge(rooms)
        override fun reconnectRealtimeIfNeeded() = vm.reconnectRealtimeIfNeeded()
        override fun setLastRoomsSyncedAtMs(atMs: Long) { vm.lastRoomsSyncedAtMs = atMs }
        override fun schedulePersistChatSnapshot() = vm.schedulePersistChatSnapshot()
        override fun applyChatHistoryClearedFromServer() = vm.applyChatHistoryClearedFromServer()
    }

    val roomsListSync = com.lastasylum.alliance.data.chat.sync.ChatRoomsListSync(
        scope = scope,
        repository = repository,
        usersRepository = vm.usersRepositoryInternal,
        chatRoomPreferences = vm.chatRoomPreferencesInternal,
        host = roomsListSyncHost,
    )

    val roomOpenSyncHost = object : com.lastasylum.alliance.data.chat.sync.ChatRoomOpenSync.Host {
        override val currentUserId: String get() = vm.currentUserIdInternal
        override fun isActiveSelectedRoom(roomId: String): Boolean = vm.isActiveSelectedRoom(roomId)
        override fun stateSnapshot(): ChatState = vm.vmState.value
        override fun updateState(transform: (ChatState) -> ChatState) { vm.vmState.update(transform) }
        override fun setState(state: ChatState) { vm.vmState.value = state }
        override fun clearTypingForRoomOpen() = vm.clearTypingForRoomOpen()
        override fun setOtherReadUpto(roomId: String) {
            vm._otherReadUptoMessageId.value = vm.otherReadUptoByRoom[roomId]
        }
        override suspend fun hydratePeerReadCursor(roomId: String, force: Boolean) =
            vm.hydratePeerReadCursor(roomId, force)
        override fun isAllianceRaidRoom(roomId: String): Boolean = vm.isAllianceRaidRoom(roomId)
        override fun isGlobalChatRoom(roomId: String, rooms: List<ChatRoomDto>): Boolean =
            vm.isGlobalChatRoom(roomId, rooms)
        override suspend fun loadTeamProfileGate(
            isGlobalRoom: Boolean,
            hadCachedMessages: Boolean,
            messagesAlreadyInState: Boolean,
        ): Boolean = vm.loadTeamProfileGateForOpen(isGlobalRoom, hadCachedMessages, messagesAlreadyInState)
        override fun clearUnreadForRoomIfViewing(rooms: List<ChatRoomDto>, roomId: String, treatAsViewing: Boolean) =
            vm.clearUnreadForRoomIfViewing(rooms, roomId, treatAsViewing)
        override fun roomMessageCache(roomId: String): ChatRoomMessageCache? = vm.roomMessageCache[roomId]
        override fun updateRoomMessageCache(roomId: String, cache: ChatRoomMessageCache) {
            vm.roomMessageCache[roomId] = cache
        }
        override fun messagesWithoutLocallyRemoved(messages: List<ChatMessage>) =
            vm.messagesWithoutLocallyRemoved(messages)
        override fun filterMessagesForRoom(messages: List<ChatMessage>, roomId: String) =
            vm.filterMessagesForRoom(messages, roomId)
        override fun capMessagesForMemory(messages: List<ChatMessage>) = vm.capMessagesForMemory(messages)
        override fun knownMessageIds(): MutableSet<String> = vm.knownMessageIds
        override fun messageIdIndex(): MutableMap<String, Int> = vm.messageIdIndex
        override fun setListDerivedEmpty() { vm._listDerived.value = ChatMessagesListDerived.Empty }
        override fun publishMessagesDerived(messages: List<ChatMessage>) = vm.publishMessagesDerived(messages)
        override fun shouldAutoMarkReadSelectedRoom(): Boolean = vm.shouldAutoMarkReadSelectedRoom()
        override fun markRoomReadUpTo(roomId: String, messageId: String) {
            scope.launch { vm.markRoomReadUpTo(roomId, messageId) }
        }
        override fun schedulePersistChatSnapshot() = vm.schedulePersistChatSnapshot()
        override fun setLaunchWarmupNeedsMessages(needs: Boolean) { vm.launchWarmupNeedsMessages = needs }
        override fun connectRealtime(rooms: List<ChatRoomDto>) = vm.reconnectRealtimeIfNeeded()
        override fun applyOpenRoomLoadingState(roomId: String, rooms: List<ChatRoomDto>, hasTeam: Boolean) =
            vm.applyOpenRoomLoadingState(roomId, rooms, hasTeam)
        override fun applyOpenRoomCachedState(
            roomId: String,
            rooms: List<ChatRoomDto>,
            hasTeam: Boolean,
            filteredCache: List<ChatMessage>,
            hasMoreOlder: Boolean,
            messagesAlreadyInState: Boolean,
        ) = vm.applyOpenRoomCachedState(
            roomId, rooms, hasTeam, filteredCache, hasMoreOlder, messagesAlreadyInState,
        )
        override fun applyOpenRoomLoadError(message: String) {
            vm.vmState.update { it.copy(isLoading = false, error = message) }
        }
    }

    val roomOpenSync = com.lastasylum.alliance.data.chat.sync.ChatRoomOpenSync(
        repository = repository,
        syncEngine = vm.chatSyncEngine,
        roomStoreBindings = vm.roomStoreBindings,
        pagingSync = roomPagingSync,
        rehydrateSync = rehydrateSync,
        host = roomOpenSyncHost,
    )

    val bootstrapSyncHost = object : com.lastasylum.alliance.data.chat.sync.ChatBootstrapSync.Host {
        override fun stateSnapshot(): ChatState = vm.vmState.value
        override fun updateState(transform: (ChatState) -> ChatState) { vm.vmState.update(transform) }
        override fun overlayRaidAlreadyReady(rooms: List<ChatRoomDto>): Boolean =
            vm.overlayRaidAlreadyReady(rooms)
        override fun overlayHubAlreadyReady(rooms: List<ChatRoomDto>): Boolean =
            vm.overlayHubAlreadyReady(rooms)
        override fun recomputeRoomUnreadBadges() = vm.recomputeRoomUnreadBadges()
        override fun scheduleSyncRoomsFromServer(reconfirmVisibleRoom: Boolean) =
            vm.syncRoomsFromServer(reconfirmVisibleRoom)
        override fun refreshMessagesInBackground(roomId: String, force: Boolean) =
            roomPagingSync.refreshMessagesInBackground(roomId, force)
        override suspend fun resolveRoomsForBootstrap(
            preferAllianceHubRoom: Boolean,
            preferOverlayRaidRoom: Boolean,
        ) = roomsListSync.resolveRoomsForBootstrap(preferAllianceHubRoom, preferOverlayRaidRoom)
        override fun fallbackRoomsOnBootstrapError(error: Throwable): List<ChatRoomDto>? =
            vm.fallbackRoomsOnBootstrapError(error)
        override fun applyBootstrapEmptyRoomsError(message: String) = vm.applyBootstrapEmptyRoomsError(message)
        override fun applyBootstrapError(message: String) = vm.applyBootstrapError(message)
        override suspend fun applyRoomsFromServer(serverRooms: List<ChatRoomDto>) =
            vm.applyRoomsFromServer(serverRooms)
        override fun syncRaidRoomPreference(rooms: List<ChatRoomDto>) = vm.syncRaidRoomPreference(rooms)
        override fun syncOverlayAllianceHubBadge(rooms: List<ChatRoomDto>) = vm.syncOverlayAllianceHubBadge(rooms)
        override fun schedulePersistChatSnapshot() = vm.schedulePersistChatSnapshot()
        override fun resolveStartupRoomId(rooms: List<ChatRoomDto>, preferOverlayRaidRoom: Boolean) =
            vm.resolveStartupRoomId(rooms, preferOverlayRaidRoom)
        override fun setSelectedRoomPref(roomId: String) =
            vm.chatRoomPreferencesInternal.setSelectedRoomId(roomId)
        override suspend fun reconcileStaleServerUnread(merged: List<ChatRoomDto>, raw: List<ChatRoomDto>) =
            vm.reconcileStaleServerUnread(merged, raw)
        override fun roomMessageCache(roomId: String): ChatRoomMessageCache? = vm.roomMessageCache[roomId]
        override fun seedRoomMessageCache(roomId: String, cache: ChatRoomMessageCache) {
            vm.roomMessageCache[roomId] = cache
        }
        override suspend fun openRoom(
            roomId: String,
            rooms: List<ChatRoomDto>,
            hadCachedMessages: Boolean,
            messagesAlreadyInState: Boolean,
            deferNetworkMessages: Boolean,
        ) = roomOpenSync.openRoom(roomId, rooms, hadCachedMessages, messagesAlreadyInState, deferNetworkMessages)
        override fun setLaunchWarmupNeedsMessages(needs: Boolean) { vm.launchWarmupNeedsMessages = needs }
    }

    val bootstrapSync = com.lastasylum.alliance.data.chat.sync.ChatBootstrapSync(
        bootstrapMutex, roomsListSync, bootstrapSyncHost,
    )

    val overlaySyncHost = object : com.lastasylum.alliance.data.chat.sync.ChatOverlaySync.Host {
        override fun stateSnapshot(): ChatState = vm.vmState.value
        override fun updateState(transform: (ChatState) -> ChatState) { vm.vmState.update(transform) }
        override fun setState(state: ChatState) { vm.vmState.value = state }
        override fun overlayChatPanelVisible(): Boolean = vm.overlayChatPanelVisible
        override fun isActiveSelectedRoom(roomId: String): Boolean = vm.isActiveSelectedRoom(roomId)
        override fun overlayRaidAlreadyReady(rooms: List<ChatRoomDto>): Boolean {
            val raidId = vm.allianceRaidRoomId(rooms) ?: return false
            val st = vm.vmState.value
            return !st.isRoomsLoading && !st.isLoading && st.error.isNullOrBlank() &&
                st.selectedRoomId == raidId && st.messages.isNotEmpty() &&
                vm.messagesBelongToRoom(st.messages, raidId)
        }
        override fun overlayHubAlreadyReady(rooms: List<ChatRoomDto>): Boolean {
            val hubId = vm.allianceHubRoomId(rooms) ?: return false
            val st = vm.vmState.value
            return !st.isRoomsLoading && !st.isLoading && st.error.isNullOrBlank() &&
                st.selectedRoomId == hubId && st.messages.isNotEmpty() &&
                vm.messagesBelongToRoom(st.messages, hubId)
        }
        override fun allianceHubRoomId(rooms: List<ChatRoomDto>): String? = vm.allianceHubRoomId(rooms)
        override fun allianceRaidRoomId(rooms: List<ChatRoomDto>): String? = vm.allianceRaidRoomId(rooms)
        override fun applyRoomsFromServer(serverRooms: List<ChatRoomDto>): List<ChatRoomDto> =
            vm.applyRoomsFromServer(serverRooms)
        override fun resolveStartupRoomId(rooms: List<ChatRoomDto>, preferOverlayRaidRoom: Boolean) =
            vm.resolveStartupRoomId(rooms, preferOverlayRaidRoom)
        override fun messagesBelongToRoom(messages: List<ChatMessage>, roomId: String) =
            vm.messagesBelongToRoom(messages, roomId)
        override fun diskChatRoomsOrNull(): List<ChatRoomDto>? = vm.diskChatRoomsOrNull()
        override fun roomMessageCache(roomId: String): ChatRoomMessageCache? = vm.roomMessageCache[roomId]
        override fun updateRoomMessageCache(roomId: String, cache: ChatRoomMessageCache) {
            vm.roomMessageCache[roomId] = cache
        }
        override fun loadRoomSnapshotFromStore(roomId: String): ChatRoomMessageCache? =
            vm.loadRoomSnapshotFromStore(roomId)
        override fun messagesWithoutLocallyRemoved(messages: List<ChatMessage>) =
            vm.messagesWithoutLocallyRemoved(messages)
        override fun filterMessagesForRoom(messages: List<ChatMessage>, roomId: String) =
            vm.filterMessagesForRoom(messages, roomId)
        override fun knownMessageIds(): MutableSet<String> = vm.knownMessageIds
        override fun messageIdIndex(): MutableMap<String, Int> = vm.messageIdIndex
        override fun setListDerivedEmpty() { vm._listDerived.value = ChatMessagesListDerived.Empty }
        override fun publishMessagesDerived(messages: List<ChatMessage>) = vm.publishMessagesDerived(messages)
        override fun refreshPinBarForSelectedRoom() = vm.refreshPinBarForSelectedRoom()
        override fun persistRoomMessagesToCache(roomId: String, messages: List<ChatMessage>, hasMoreOlder: Boolean) =
            vm.persistRoomMessagesToCache(roomId, messages, hasMoreOlder)
    }

    val overlaySync = com.lastasylum.alliance.data.chat.sync.ChatOverlaySync(
        rehydrateSync, roomPagingSync, overlaySyncHost,
    )
}
