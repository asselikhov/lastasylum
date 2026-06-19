package com.lastasylum.alliance.data.chat.sync

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.data.chat.store.MessageStore
import com.lastasylum.alliance.data.isObjectIdNewer
import com.lastasylum.alliance.ui.OVERLAY_PANEL_LOAD_MAX_MS
import com.lastasylum.alliance.ui.chat.chatMessagesListContentEqual
import com.lastasylum.alliance.ui.chat.filterMessagesForRoom
import com.lastasylum.alliance.ui.chat.mergeLoadedPageWithExisting
import com.lastasylum.alliance.ui.chat.mergeOlderPage
import com.lastasylum.alliance.ui.chat.rebuildMessageIdIndex
import com.lastasylum.alliance.ui.chat.hasOptimisticOutgoingPending
import com.lastasylum.alliance.ui.chat.shouldSkipBackgroundMessageRefresh
import com.lastasylum.alliance.ui.chat.ChatState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Background refresh + older-page paging for the active room timeline.
 */
class ChatRoomPagingSync(
    private val scope: CoroutineScope,
    private val repository: ChatRepository,
    private val messageStore: MessageStore,
    private val host: Host,
) {
    interface Host {
        val currentUserId: String
        val messageMemoryCap: Int

        fun stateSnapshot(): ChatState
        fun selectedRoomId(): String?
        fun overlayChatPanelVisible(): Boolean
        fun isChatTabActive(): Boolean
        fun isAllianceRaidRoom(roomId: String): Boolean

        fun messagesForRoomMerge(roomId: String): List<ChatMessage>
        fun messagesWithoutLocallyRemoved(messages: List<ChatMessage>): List<ChatMessage>
        fun filterMessagesForRoom(messages: List<ChatMessage>, roomId: String): List<ChatMessage>
        fun protectedSocketMessageIds(): Set<String>
        fun mergeAnchorDropLogger(roomId: String): (String) -> Unit
        fun locallyRemovedMessageIds(): Set<String>
        fun knownMessageIds(): MutableSet<String>
        fun messageIdIndex(): MutableMap<String, Int>
        fun capMessagesForMemory(messages: List<ChatMessage>): List<ChatMessage>

        fun isActiveSelectedRoom(roomId: String): Boolean
        fun shouldAutoMarkReadSelectedRoom(): Boolean
        fun resolvedLastReadForRoom(roomId: String): String?

        fun updateRoomMessageCache(roomId: String, cache: ChatRoomMessageCache)
        fun roomMessageCache(roomId: String): ChatRoomMessageCache?
        fun lastBackgroundRefreshAtMs(roomId: String): Long
        fun setLastBackgroundRefreshAtMs(roomId: String, atMs: Long)
        fun forceBackgroundRefreshAfterReconnect(): Boolean
        fun setForceBackgroundRefreshAfterReconnect(value: Boolean)
        fun isChatSocketConnected(): Boolean

        fun applyLoadedPageToUi(
            roomId: String,
            capped: List<ChatMessage>,
            hasMoreOlder: Boolean,
        )
        fun applyMergedPageToUi(
            roomId: String,
            merged: List<ChatMessage>,
            hasMoreOlder: Boolean,
        )
        fun applyLoadingOlder(loading: Boolean)
        fun applyOlderPageToUi(messages: List<ChatMessage>)
        fun applyOlderPagingComplete(
            messages: List<ChatMessage>? = null,
            hasMoreOlder: Boolean,
        )
        fun applyOlderPagingError(message: String)
        fun applyPagingError(errorMessage: String)
        fun applyOverlayLoadTimeout(message: String)
        fun markRoomReadUpTo(roomId: String, messageId: String)
        fun schedulePersistChatSnapshot()
        fun publishMessagesDerived(messages: List<ChatMessage>)
        fun publishMessagesDerivedImmediate(messages: List<ChatMessage>)
        fun loadErrorString(throwable: Throwable): String
        fun overlayTimeoutString(): String
        fun isRoomAuthoritativeEmpty(roomId: String): Boolean
        fun clearRoomAuthoritativeEmpty(roomId: String)
    }

    private val backgroundRefreshJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    fun cancelBackgroundRefresh(roomId: String) {
        val rid = roomId.trim()
        if (rid.isEmpty()) return
        backgroundRefreshJobs.remove(rid)?.cancel()
    }

    fun refreshMessagesInBackground(roomId: String, force: Boolean = false) {
        if (!force && shouldSkipBackgroundMessageRefreshForRoom(roomId)) return
        val rid = roomId.trim()
        if (rid.isEmpty()) return
        val inFlight = backgroundRefreshJobs[rid]
        if (!force && inFlight?.isActive == true) return
        if (force) {
            cancelBackgroundRefresh(roomId)
        }
        val deferMs = when {
            host.isActiveSelectedRoom(roomId) -> 0L
            ChatSessionCache.getFreshMessages(roomId) == null -> 0L
            host.overlayChatPanelVisible() && host.isAllianceRaidRoom(roomId) -> 0L
            else -> CHAT_BACKGROUND_MESSAGE_REFRESH_DEFER_MS
        }
        backgroundRefreshJobs[rid] = scope.launch {
            try {
            if (deferMs > 0L) delay(deferMs)
            val isSelectedRoom = host.selectedRoomId() == roomId
            val overlayEmptyLoad = isSelectedRoom &&
                host.overlayChatPanelVisible() &&
                host.stateSnapshot().messages.isEmpty()
            val loadResult = if (overlayEmptyLoad) {
                withTimeoutOrNull(OVERLAY_PANEL_LOAD_MAX_MS) {
                    repository.loadRecentMessages(roomId, beforeMessageId = null, limit = CHAT_PAGE_SIZE)
                }
            } else {
                repository.loadRecentMessages(roomId, beforeMessageId = null, limit = CHAT_PAGE_SIZE)
            }
            if (loadResult == null) {
                if (overlayEmptyLoad) {
                    val snapshot = host.stateSnapshot()
                    if (snapshot.rooms.isNotEmpty() && snapshot.messages.isEmpty()) {
                        host.applyOverlayLoadTimeout("")
                    } else {
                        host.applyOverlayLoadTimeout(host.overlayTimeoutString())
                    }
                }
                return@launch
            }
            loadResult
                .onSuccess { loaded ->
                    host.setLastBackgroundRefreshAtMs(roomId, System.currentTimeMillis())
                    host.setForceBackgroundRefreshAfterReconnect(false)
                    val hasMoreOlder = loaded.size >= CHAT_PAGE_SIZE
                    val current = host.messagesForRoomMerge(roomId)
                    val merged = withContext(Dispatchers.Default) {
                        val raw = mergeLoadedPageWithExisting(
                            existing = current,
                            loaded = loaded,
                            maxMessages = host.messageMemoryCap,
                            excludedMessageIds = host.locallyRemovedMessageIds(),
                            roomId = roomId,
                            protectedSocketMessageIds = host.protectedSocketMessageIds(),
                            onAnchorDrop = host.mergeAnchorDropLogger(roomId),
                            authoritativeEmpty = host.isRoomAuthoritativeEmpty(roomId),
                            currentUserId = host.currentUserId,
                        )
                        host.filterMessagesForRoom(raw, roomId)
                    }
                    host.updateRoomMessageCache(
                        roomId,
                        ChatRoomMessageCache(messages = merged, hasMoreOlder = hasMoreOlder),
                    )
                    ChatSessionCache.updateMessages(roomId, merged)
                    if (!isSelectedRoom || host.selectedRoomId() != roomId) return@onSuccess
                    if (!host.overlayChatPanelVisible() && !host.isChatTabActive()) return@onSuccess
                    val unchanged = withContext(Dispatchers.Default) {
                        chatMessagesListContentEqual(current, merged)
                    }
                    if (unchanged) {
                        host.applyMergedPageToUi(roomId, merged, hasMoreOlder)
                        return@onSuccess
                    }
                    val known = host.knownMessageIds()
                    val index = host.messageIdIndex()
                    known.clear()
                    index.clear()
                    known.addAll(merged.mapNotNull { it._id })
                    rebuildMessageIdIndex(merged, index)
                    host.applyMergedPageToUi(roomId, merged, hasMoreOlder)
                    host.publishMessagesDerived(merged)
                }
                .onFailure { e ->
                    if (
                        host.overlayChatPanelVisible() &&
                        isSelectedRoom &&
                        host.stateSnapshot().messages.isEmpty()
                    ) {
                        if (host.stateSnapshot().rooms.isEmpty()) {
                            host.applyPagingError(host.loadErrorString(e))
                        } else {
                            host.applyOverlayLoadTimeout("")
                        }
                    }
                }
            } finally {
                backgroundRefreshJobs.remove(roomId.trim())
                if (host.isRoomAuthoritativeEmpty(roomId)) {
                    host.clearRoomAuthoritativeEmpty(roomId)
                }
            }
        }
    }

    fun applyLoadedMessagePage(
        roomId: String,
        loaded: List<ChatMessage>,
        pageSizeForHasMore: Int,
    ) {
        val current = host.messagesForRoomMerge(roomId)
        val merged = host.filterMessagesForRoom(
            mergeLoadedPageWithExisting(
                existing = current,
                loaded = loaded,
                maxMessages = host.messageMemoryCap,
                excludedMessageIds = host.locallyRemovedMessageIds(),
                roomId = roomId,
                protectedSocketMessageIds = host.protectedSocketMessageIds(),
                onAnchorDrop = host.mergeAnchorDropLogger(roomId),
                authoritativeEmpty = host.isRoomAuthoritativeEmpty(roomId),
                currentUserId = host.currentUserId,
            ),
            roomId,
        )
        ChatSessionCache.updateMessages(roomId, merged)
        val known = host.knownMessageIds()
        val index = host.messageIdIndex()
        known.clear()
        index.clear()
        known.addAll(merged.mapNotNull { it._id })
        val capped = host.capMessagesForMemory(merged)
        rebuildMessageIdIndex(capped, index)
        val hasMoreOlder = loaded.size >= pageSizeForHasMore
        host.updateRoomMessageCache(
            roomId,
            ChatRoomMessageCache(messages = capped, hasMoreOlder = hasMoreOlder),
        )
        if (!host.isActiveSelectedRoom(roomId)) {
            host.schedulePersistChatSnapshot()
            return
        }
        host.applyLoadedPageToUi(roomId, capped, hasMoreOlder)
        host.publishMessagesDerived(capped)
        host.schedulePersistChatSnapshot()
    }

    suspend fun loadOlderMessagesAwait(ignoreHiddenWatermark: Boolean = false): Boolean {
        val roomId = host.selectedRoomId() ?: return false
        val snapshot = host.stateSnapshot()
        val oldestId = snapshot.messages.lastOrNull()?._id ?: return false
        if (!snapshot.hasMoreOlder || snapshot.isLoadingOlder) return false
        if (!ignoreHiddenWatermark && snapshot.isLoading) return false
        host.applyLoadingOlder(true)
        if (host.currentUserId.isNotBlank()) {
            val storeMessages = withContext(Dispatchers.IO) {
                messageStore.getMessages(host.currentUserId, roomId)
            }
            val diskOlder = storeMessages.filter { msg ->
                val id = msg._id?.trim().orEmpty()
                id.isNotEmpty() &&
                    isObjectIdNewer(oldestId, id) &&
                    id !in host.knownMessageIds()
            }
            if (diskOlder.isNotEmpty()) {
                val merged = mergeOlderPage(snapshot.messages, diskOlder, host.knownMessageIds())
                rebuildMessageIdIndex(merged, host.messageIdIndex())
                host.applyOlderPageToUi(merged)
                host.publishMessagesDerived(merged)
            }
        }
        val result = repository.loadRecentMessages(
            roomId = roomId,
            beforeMessageId = oldestId,
            limit = CHAT_PAGE_SIZE,
        )
        result
            .onSuccess { older ->
                if (older.isEmpty()) {
                    host.applyOlderPagingComplete(hasMoreOlder = false)
                } else {
                    val visibleOlder = if (ignoreHiddenWatermark) {
                        older
                    } else {
                        host.filterMessagesForRoom(older, roomId)
                    }
                    val merged = mergeOlderPage(host.stateSnapshot().messages, visibleOlder, host.knownMessageIds())
                    rebuildMessageIdIndex(merged, host.messageIdIndex())
                    host.applyOlderPagingComplete(
                        messages = merged,
                        hasMoreOlder = older.size >= CHAT_PAGE_SIZE,
                    )
                    if (ignoreHiddenWatermark) {
                        host.publishMessagesDerivedImmediate(merged)
                    } else {
                        host.publishMessagesDerived(merged)
                    }
                }
            }
            .onFailure { e ->
                host.applyOlderPagingError(host.loadErrorString(e))
            }
        return result.isSuccess && result.getOrNull()?.isNotEmpty() == true
    }

    private fun shouldSkipBackgroundMessageRefreshForRoom(roomId: String): Boolean {
        val rid = roomId.trim()
        if (rid.isEmpty()) return false
        if (host.isActiveSelectedRoom(rid)) return false
        val visible = host.filterMessagesForRoom(host.stateSnapshot().messages, rid)
        if (hasOptimisticOutgoingPending(visible, host.currentUserId)) return false
        val sessionCache = ChatSessionCache.getFreshMessages(rid)
        val roomCache = host.roomMessageCache(rid)?.messages?.let {
            host.messagesWithoutLocallyRemoved(host.filterMessagesForRoom(it, rid))
        }
        if (!roomCache.isNullOrEmpty() && roomCache.size > visible.size) return false
        return shouldSkipBackgroundMessageRefresh(
            visible = visible,
            sessionCache = sessionCache,
            roomCache = roomCache,
            pageSize = CHAT_PAGE_SIZE,
            lastRestSyncAtMs = host.lastBackgroundRefreshAtMs(rid),
            forceAfterReconnect = host.forceBackgroundRefreshAfterReconnect(),
            overlayPanelVisible = host.overlayChatPanelVisible(),
            socketConnected = host.isChatSocketConnected(),
        )
    }
}
