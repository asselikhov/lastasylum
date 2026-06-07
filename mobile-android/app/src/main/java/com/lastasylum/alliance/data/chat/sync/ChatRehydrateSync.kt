package com.lastasylum.alliance.data.chat.sync

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.ui.chat.ChatState
import com.lastasylum.alliance.ui.chat.chatMessagesListContentEqual
import com.lastasylum.alliance.ui.chat.filterMessagesForRoom
import com.lastasylum.alliance.ui.chat.mergeVisibleMessagesWithRoomCache
import com.lastasylum.alliance.ui.chat.rebuildMessageIdIndex

/** Merge socket stash / RAM cache into the visible timeline. */
class ChatRehydrateSync(
    private val host: Host,
) {
    interface Host {
        val messageMemoryCap: Int
        val currentUserId: String

        fun stateSnapshot(): ChatState
        fun selectedRoomId(): String?
        fun isActiveSelectedRoom(roomId: String): Boolean

        fun messagesWithoutLocallyRemoved(messages: List<ChatMessage>): List<ChatMessage>
        fun filterMessagesForRoom(messages: List<ChatMessage>, roomId: String): List<ChatMessage>
        fun hiddenBeforeForRoom(roomId: String): String?
        fun locallyRemovedMessageIds(): Set<String>
        fun knownMessageIds(): MutableSet<String>
        fun messageIdIndex(): MutableMap<String, Int>

        fun roomMessageCache(roomId: String): ChatRoomMessageCache?
        fun updateRoomMessageCache(roomId: String, cache: ChatRoomMessageCache)
        fun capMessagesForMemory(messages: List<ChatMessage>): List<ChatMessage>

        fun applyRehydratedMessages(
            roomId: String,
            merged: List<ChatMessage>,
            hasMoreOlder: Boolean,
        )
        fun publishMessagesDerived(messages: List<ChatMessage>)
        fun publishMessagesDerivedImmediate(messages: List<ChatMessage>)
    }

    fun rehydrateSelectedRoomMessagesFromCache(): Boolean {
        val roomId = host.selectedRoomId()?.trim().orEmpty()
        if (roomId.isEmpty()) return false
        return rehydrateRoomMessagesFromCache(roomId)
    }

    fun rehydrateRoomMessagesFromCache(roomId: String): Boolean {
        val rid = roomId.trim()
        if (rid.isEmpty()) return false
        val cachedEntry = host.roomMessageCache(rid)
            ?: sessionCacheEntry(rid)
        val visible = host.messagesWithoutLocallyRemoved(
            host.filterMessagesForRoom(host.stateSnapshot().messages, rid),
        )
        if (cachedEntry == null) {
            if (visible.isEmpty()) return false
            host.updateRoomMessageCache(
                rid,
                ChatRoomMessageCache(
                    messages = host.capMessagesForMemory(visible),
                    hasMoreOlder = host.stateSnapshot().hasMoreOlder,
                ),
            )
            ChatSessionCache.updateMessages(rid, host.roomMessageCache(rid)!!.messages)
            return false
        }
        val cached = host.messagesWithoutLocallyRemoved(
            host.filterMessagesForRoom(cachedEntry.messages, rid),
        )
        val merged = mergeVisibleMessagesWithRoomCache(
            visible = visible,
            cached = cached,
            roomId = rid,
            maxMessages = host.messageMemoryCap,
            excludedMessageIds = host.locallyRemovedMessageIds(),
            hiddenBeforeMessageId = host.hiddenBeforeForRoom(rid),
            currentUserId = host.currentUserId,
        )
        if (chatMessagesListContentEqual(cached, merged) &&
            chatMessagesListContentEqual(visible, merged)
        ) {
            return false
        }
        val hasMoreOlder = cachedEntry.hasMoreOlder
        host.updateRoomMessageCache(rid, ChatRoomMessageCache(merged, hasMoreOlder))
        ChatSessionCache.updateMessages(rid, merged)
        if (!host.isActiveSelectedRoom(rid)) return true
        val known = host.knownMessageIds()
        val index = host.messageIdIndex()
        known.clear()
        index.clear()
        known.addAll(merged.mapNotNull { it._id })
        rebuildMessageIdIndex(merged, index)
        host.applyRehydratedMessages(rid, merged, hasMoreOlder)
        host.publishMessagesDerivedImmediate(
            host.filterMessagesForRoom(host.stateSnapshot().messages, rid),
        )
        return true
    }

    fun snapshotSelectedRoomToMessageCache() {
        val roomId = host.selectedRoomId()?.trim().orEmpty()
        if (roomId.isEmpty()) return
        val visible = host.messagesWithoutLocallyRemoved(
            host.filterMessagesForRoom(host.stateSnapshot().messages, roomId),
        )
        if (visible.isEmpty()) return
        val capped = host.capMessagesForMemory(visible)
        val prev = host.roomMessageCache(roomId)
        host.updateRoomMessageCache(
            roomId,
            ChatRoomMessageCache(
                messages = capped,
                hasMoreOlder = prev?.hasMoreOlder ?: host.stateSnapshot().hasMoreOlder,
            ),
        )
        ChatSessionCache.updateMessages(roomId, capped)
    }

    private fun sessionCacheEntry(roomId: String): ChatRoomMessageCache? {
        val messages = ChatSessionCache.getFreshMessages(roomId) ?: return null
        if (messages.isEmpty()) return null
        return ChatRoomMessageCache(messages = messages, hasMoreOlder = true)
    }
}
