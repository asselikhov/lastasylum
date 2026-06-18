package com.lastasylum.alliance.data.chat.sync

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.ui.chat.ChatState
import com.lastasylum.alliance.ui.chat.capNewestFirst

/** Overlay panel cache-first room/message prime without blocking on network bootstrap. */
class ChatOverlaySync(
    private val rehydrateSync: ChatRehydrateSync,
    private val pagingSync: ChatRoomPagingSync,
    private val host: Host,
) {
    interface Host {
        fun stateSnapshot(): ChatState
        fun updateState(transform: (ChatState) -> ChatState)
        fun setState(state: ChatState)

        fun overlayChatPanelVisible(): Boolean
        fun isActiveSelectedRoom(roomId: String): Boolean
        fun overlayRaidAlreadyReady(rooms: List<ChatRoomDto>): Boolean
        fun overlayHubAlreadyReady(rooms: List<ChatRoomDto>): Boolean

        fun allianceHubRoomId(rooms: List<ChatRoomDto>): String?
        fun allianceRaidRoomId(rooms: List<ChatRoomDto>): String?

        fun applyRoomsFromServer(serverRooms: List<ChatRoomDto>): List<ChatRoomDto>
        fun resolveStartupRoomId(
            rooms: List<ChatRoomDto>,
            preferOverlayRaidRoom: Boolean,
        ): String
        fun messagesBelongToRoom(messages: List<ChatMessage>, roomId: String): Boolean

        fun diskChatRoomsOrNull(): List<ChatRoomDto>?
        fun roomMessageCache(roomId: String): ChatRoomMessageCache?
        fun updateRoomMessageCache(roomId: String, cache: ChatRoomMessageCache)
        fun loadRoomSnapshotFromStore(roomId: String): ChatRoomMessageCache?

        fun messagesWithoutLocallyRemoved(messages: List<ChatMessage>): List<ChatMessage>
        fun filterMessagesForRoom(messages: List<ChatMessage>, roomId: String): List<ChatMessage>
        fun knownMessageIds(): MutableSet<String>
        fun messageIdIndex(): MutableMap<String, Int>

        fun setListDerivedEmpty()
        fun publishMessagesDerived(messages: List<ChatMessage>)
        fun refreshPinBarForSelectedRoom()
        fun persistRoomMessagesToCache(roomId: String, messages: List<ChatMessage>, hasMoreOlder: Boolean)
        fun isChatSocketConnected(): Boolean
    }

    fun primeOverlayChatFromCache(
        preferAllianceHubRoom: Boolean = true,
        preferOverlayRaidRoom: Boolean = false,
    ) {
        if (preferOverlayRaidRoom && host.overlayRaidAlreadyReady(host.stateSnapshot().rooms)) {
            host.allianceRaidRoomId(host.stateSnapshot().rooms)?.let { raidId ->
                if (host.isActiveSelectedRoom(raidId)) {
                    rehydrateSync.rehydrateRoomMessagesFromCache(raidId)
                }
                scheduleOverlayRoomHistorySync(raidId)
            }
            host.updateState { it.copy(isLoading = false, isRoomsLoading = false) }
            host.refreshPinBarForSelectedRoom()
            return
        }
        if (preferAllianceHubRoom && !preferOverlayRaidRoom) {
            host.allianceHubRoomId(host.stateSnapshot().rooms)?.let { hubId ->
                if (host.isActiveSelectedRoom(hubId)) {
                    rehydrateSync.rehydrateRoomMessagesFromCache(hubId)
                }
            }
            if (host.overlayHubAlreadyReady(host.stateSnapshot().rooms)) {
                host.updateState { it.copy(isLoading = false, isRoomsLoading = false) }
                host.refreshPinBarForSelectedRoom()
                return
            }
            if (overlayHubRoomsReady(host.stateSnapshot().rooms)) {
                val hubId = host.allianceHubRoomId(host.stateSnapshot().rooms) ?: return
                host.updateState { st ->
                    st.copy(
                        isLoading = false,
                        isRoomsLoading = false,
                        error = null,
                        selectedRoomId = hubId,
                        rooms = st.rooms.ifEmpty { host.stateSnapshot().rooms },
                        messages = if (st.selectedRoomId == hubId) st.messages else emptyList(),
                    )
                }
                if (host.stateSnapshot().messages.isEmpty()) {
                    host.setListDerivedEmpty()
                }
                scheduleOverlayRoomHistorySync(hubId)
                host.refreshPinBarForSelectedRoom()
                return
            }
        }
        val roomsRaw = ChatSessionCache.getFreshRooms()
            ?: host.stateSnapshot().rooms.takeIf { it.isNotEmpty() }
            ?: host.diskChatRoomsOrNull()
            ?: return
        val rooms = host.applyRoomsFromServer(roomsRaw)
        if (ChatSessionCache.getFreshRooms() == null) {
            ChatSessionCache.update(rooms)
        }
        if (rooms.isEmpty()) return
        val roomId = host.resolveStartupRoomId(
            rooms = rooms,
            preferOverlayRaidRoom = preferOverlayRaidRoom,
        )
        val snapshot = host.stateSnapshot()
        if (snapshot.selectedRoomId == roomId &&
            snapshot.messages.isNotEmpty() &&
            host.messagesBelongToRoom(snapshot.messages, roomId)
        ) {
            rehydrateSync.rehydrateRoomMessagesFromCache(roomId)
            host.updateState { it.copy(isLoading = false, isRoomsLoading = false) }
            host.refreshPinBarForSelectedRoom()
            return
        }
        val cached = host.roomMessageCache(roomId)
            ?: ChatSessionCache.getFreshMessages(roomId)?.let { sessionMessages ->
                val scrubbed = host.filterMessagesForRoom(
                    host.messagesWithoutLocallyRemoved(sessionMessages),
                    roomId,
                )
                val capped = capNewestFirst(scrubbed, CHAT_PAGE_SIZE)
                ChatRoomMessageCache(
                    messages = capped,
                    hasMoreOlder = scrubbed.size >= CHAT_PAGE_SIZE,
                ).also { host.updateRoomMessageCache(roomId, it) }
            }
            ?: host.loadRoomSnapshotFromStore(roomId)
        if (cached == null || cached.messages.isEmpty()) {
            val switchingRoom = snapshot.selectedRoomId != roomId ||
                !host.messagesBelongToRoom(snapshot.messages, roomId)
            if (switchingRoom) {
                host.knownMessageIds().clear()
                host.messageIdIndex().clear()
                host.setListDerivedEmpty()
            }
            if (rooms.any { it.id == roomId }) {
                host.updateState { st ->
                    st.copy(
                        isLoading = false,
                        isRoomsLoading = false,
                        error = null,
                        selectedRoomId = roomId,
                        messages = if (switchingRoom) emptyList() else st.messages,
                        rooms = if (st.rooms.isEmpty()) rooms else st.rooms,
                        hasMoreOlder = cached?.hasMoreOlder ?: st.hasMoreOlder,
                    )
                }
                host.setListDerivedEmpty()
                if (host.overlayChatPanelVisible()) {
                    scheduleOverlayRoomHistorySync(roomId)
                }
                host.refreshPinBarForSelectedRoom()
                return
            }
            if (host.isActiveSelectedRoom(roomId) || snapshot.selectedRoomId.isNullOrBlank()) {
                host.updateState { st ->
                    st.copy(
                        isLoading = true,
                        isRoomsLoading = false,
                        selectedRoomId = roomId,
                        messages = if (switchingRoom) emptyList() else st.messages,
                        rooms = if (st.rooms.isEmpty()) rooms else st.rooms,
                    )
                }
            }
            return
        }
        if (!host.isActiveSelectedRoom(roomId) && !snapshot.selectedRoomId.isNullOrBlank()) {
            host.persistRoomMessagesToCache(roomId, cached.messages, cached.hasMoreOlder)
            return
        }
        host.knownMessageIds().clear()
        host.messageIdIndex().clear()
        host.knownMessageIds().addAll(cached.messages.mapNotNull { it._id })
        com.lastasylum.alliance.ui.chat.rebuildMessageIdIndex(cached.messages, host.messageIdIndex())
        host.setState(
            snapshot.copy(
                isLoading = false,
                isRoomsLoading = false,
                rooms = rooms,
                selectedRoomId = roomId,
                messages = cached.messages,
                hasMoreOlder = cached.hasMoreOlder,
                error = null,
                scrollToLatestNonce = snapshot.scrollToLatestNonce + 1L,
            ),
        )
        host.refreshPinBarForSelectedRoom()
        host.publishMessagesDerived(cached.messages)
        if (host.overlayChatPanelVisible()) {
            scheduleOverlayRoomHistorySync(roomId)
        }
    }

    fun scheduleOverlayRoomHistorySync(roomId: String) {
        val rid = roomId.trim()
        if (rid.isEmpty()) return
        pagingSync.refreshMessagesInBackground(rid, force = !host.isChatSocketConnected())
    }

    fun overlayHubAlreadyReady(rooms: List<ChatRoomDto>): Boolean {
        val hubId = host.allianceHubRoomId(rooms) ?: return false
        val st = host.stateSnapshot()
        return !st.isRoomsLoading &&
            !st.isLoading &&
            st.error.isNullOrBlank() &&
            st.selectedRoomId == hubId &&
            st.messages.isNotEmpty() &&
            host.messagesBelongToRoom(st.messages, hubId)
    }

    fun overlayHubRoomsReady(rooms: List<ChatRoomDto>): Boolean =
        overlayHubRoomsReadyForState(
            rooms = rooms,
            hubId = host.allianceHubRoomId(rooms),
            state = host.stateSnapshot(),
        )

    fun overlayRaidAlreadyReady(rooms: List<ChatRoomDto>): Boolean {
        val raidId = host.allianceRaidRoomId(rooms) ?: return false
        val st = host.stateSnapshot()
        return !st.isRoomsLoading &&
            !st.isLoading &&
            st.error.isNullOrBlank() &&
            st.selectedRoomId == raidId &&
            st.messages.isNotEmpty() &&
            host.messagesBelongToRoom(st.messages, raidId)
    }
}
