package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.data.auth.TokenStore

/** Socket.IO subscriptions and overlay listener wiring. */
class ChatRealtimeSubscriber(
    private val socketManager: ChatSocketManager,
    private val tokenStore: TokenStore,
    private val chatRoomPreferences: ChatRoomPreferences,
) {
    private val primaryRealtimeRoomIds = LinkedHashSet<String>()
    private val overlayRealtimeRoomIds = LinkedHashSet<String>()

    private var realtimeUiListener: ((ChatMessage) -> Unit)? = null
    private var realtimeDeleteListener: ((ChatMessageDeletedEvent) -> Unit)? = null
    private var realtimeTypingListener: ((ChatTypingEvent) -> Unit)? = null
    private var realtimeReadListener: ((ChatRoomReadEvent) -> Unit)? = null
    private val overlayMessageListeners =
        java.util.concurrent.CopyOnWriteArrayList<(ChatMessage) -> Unit>()
    private val overlayReactionListeners =
        java.util.concurrent.CopyOnWriteArrayList<(OverlayReactionEvent) -> Unit>()
    private val overlayChatPanelClosedListeners =
        java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    private fun realtimeRoomIdsForPrimary(primaryRoomId: String): List<String> {
        val raid = chatRoomPreferences.getRaidRoomId()
        return buildList {
            add(primaryRoomId)
            if (!raid.isNullOrBlank() && raid != primaryRoomId) add(raid)
        }
    }

    private fun realtimeRoomIdsForOverlayBootstrap(): List<String> {
        val raid = chatRoomPreferences.getRaidRoomId()
        val selected = chatRoomPreferences.getSelectedRoomId()
        return buildList {
            if (!raid.isNullOrBlank()) add(raid)
            if (!selected.isNullOrBlank() && selected !in this) add(selected)
        }
    }

    private fun mergedRealtimeRoomIds(): List<String> =
        (primaryRealtimeRoomIds + overlayRealtimeRoomIds).distinct()

    private fun ensureRealtimeSocketConnected() {
        val distinct = mergedRealtimeRoomIds()
        if (distinct.isEmpty()) return
        socketManager.connect(
            baseUrl = BuildConfig.API_BASE_URL,
            roomIds = distinct,
            tokenProvider = { tokenStore.getAccessToken() },
        )
    }

    fun connectRealtime(
        roomId: String,
        onMessage: (ChatMessage) -> Unit,
        onDeleteMessage: (ChatMessageDeletedEvent) -> Unit = {},
        onTyping: (ChatTypingEvent) -> Unit = {},
        onRead: (ChatRoomReadEvent) -> Unit = {},
    ) {
        connectRealtimeRooms(
            roomIds = realtimeRoomIdsForPrimary(roomId),
            onMessage = onMessage,
            onDeleteMessage = onDeleteMessage,
            onTyping = onTyping,
            onRead = onRead,
        )
    }

    fun connectRealtimeRooms(
        roomIds: List<String>,
        onMessage: (ChatMessage) -> Unit,
        onDeleteMessage: (ChatMessageDeletedEvent) -> Unit = {},
        onTyping: (ChatTypingEvent) -> Unit = {},
        onRead: (ChatRoomReadEvent) -> Unit = {},
    ) {
        realtimeUiListener?.let { socketManager.removeMessageListener(it) }
        realtimeDeleteListener?.let { socketManager.removeMessageDeletedListener(it) }
        realtimeTypingListener?.let { socketManager.removeTypingListener(it) }
        realtimeReadListener?.let { socketManager.removeReadListener(it) }
        realtimeUiListener = onMessage
        realtimeDeleteListener = onDeleteMessage
        realtimeTypingListener = onTyping
        realtimeReadListener = onRead
        socketManager.addMessageListener(onMessage)
        socketManager.addMessageDeletedListener(onDeleteMessage)
        socketManager.addTypingListener(onTyping)
        socketManager.addReadListener(onRead)
        primaryRealtimeRoomIds.clear()
        primaryRealtimeRoomIds.addAll(roomIds.map { it.trim() }.filter { it.isNotEmpty() })
        ensureRealtimeSocketConnected()
    }

    fun emitTypingPing(roomId: String) {
        socketManager.emitTyping(roomId)
    }

    fun emitOverlayReaction(targetUserId: String, reaction: String = "heart") {
        socketManager.emitOverlayReaction(targetUserId, reaction)
    }

    fun emitOverlayReactionBroadcast(reaction: String = "heart") {
        socketManager.emitOverlayReactionBroadcast(reaction)
    }

    fun isChatSocketConnected(): Boolean =
        socketManager.connectionState.value == ChatConnectionState.Connected

    fun addOverlayReactionListener(listener: (OverlayReactionEvent) -> Unit) {
        if (!overlayReactionListeners.contains(listener)) {
            overlayReactionListeners.add(listener)
        }
        socketManager.addOverlayReactionListener(listener)
        overlayRealtimeRoomIds.clear()
        overlayRealtimeRoomIds.addAll(realtimeRoomIdsForOverlayBootstrap())
        ensureRealtimeSocketConnected()
    }

    fun removeOverlayReactionListener(listener: (OverlayReactionEvent) -> Unit) {
        overlayReactionListeners.remove(listener)
        socketManager.removeOverlayReactionListener(listener)
        if (overlayMessageListeners.isEmpty() && overlayReactionListeners.isEmpty()) {
            overlayRealtimeRoomIds.clear()
            if (primaryRealtimeRoomIds.isEmpty()) {
                socketManager.disconnect()
            } else {
                ensureRealtimeSocketConnected()
            }
        }
    }

    fun onAccessTokenRefreshed() {
        socketManager.reconnectWithFreshToken()
    }

    fun disconnectRealtime() {
        realtimeUiListener?.let { socketManager.removeMessageListener(it) }
        realtimeDeleteListener?.let { socketManager.removeMessageDeletedListener(it) }
        realtimeTypingListener?.let { socketManager.removeTypingListener(it) }
        realtimeReadListener?.let { socketManager.removeReadListener(it) }
        realtimeUiListener = null
        realtimeDeleteListener = null
        realtimeTypingListener = null
        realtimeReadListener = null
        primaryRealtimeRoomIds.clear()
        if (overlayRealtimeRoomIds.isEmpty()) {
            socketManager.disconnect()
        } else {
            ensureRealtimeSocketConnected()
        }
    }

    fun addOverlayMessageListener(listener: (ChatMessage) -> Unit) {
        if (!overlayMessageListeners.contains(listener)) {
            overlayMessageListeners.add(listener)
        }
        socketManager.addMessageListener(listener)
        overlayRealtimeRoomIds.clear()
        overlayRealtimeRoomIds.addAll(realtimeRoomIdsForOverlayBootstrap())
        ensureRealtimeSocketConnected()
    }

    fun removeOverlayMessageListener(listener: (ChatMessage) -> Unit) {
        overlayMessageListeners.remove(listener)
        socketManager.removeMessageListener(listener)
        if (overlayMessageListeners.isEmpty() && overlayReactionListeners.isEmpty()) {
            overlayRealtimeRoomIds.clear()
            if (primaryRealtimeRoomIds.isEmpty()) {
                socketManager.disconnect()
            } else {
                ensureRealtimeSocketConnected()
            }
        }
    }

    fun addOverlayChatPanelClosedListener(listener: () -> Unit) {
        if (!overlayChatPanelClosedListeners.contains(listener)) {
            overlayChatPanelClosedListeners.add(listener)
        }
    }

    fun removeOverlayChatPanelClosedListener(listener: () -> Unit) {
        overlayChatPanelClosedListeners.remove(listener)
    }

    fun notifyOverlayChatPanelClosed() {
        overlayChatPanelClosedListeners.forEach { listener ->
            runCatching { listener() }
        }
    }

    fun resetRealtimeForLogout() {
        realtimeUiListener?.let { socketManager.removeMessageListener(it) }
        realtimeUiListener = null
        primaryRealtimeRoomIds.clear()
        overlayRealtimeRoomIds.clear()
        overlayReactionListeners.forEach { socketManager.removeOverlayReactionListener(it) }
        overlayReactionListeners.clear()
        socketManager.disconnectSocketAndClearListeners()
    }

    fun overlayMessageListenerCount(): Int = overlayMessageListeners.size

    fun dispatchOverlayHttpMessage(message: ChatMessage) {
        overlayMessageListeners.forEach { l -> runCatching { l(message) } }
    }
}
