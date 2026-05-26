package com.lastasylum.alliance.data.chat

import android.os.Handler
import android.os.Looper
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.data.auth.TokenStore
import com.lastasylum.alliance.overlay.CombatOverlayService

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
    private var realtimeRoomUnreadListener: ((ChatRoomUnreadEvent) -> Unit)? = null
    private val overlayMessageListeners =
        java.util.concurrent.CopyOnWriteArrayList<(ChatMessage) -> Unit>()
    private val overlayReactionListeners =
        java.util.concurrent.CopyOnWriteArrayList<(OverlayReactionEvent) -> Unit>()
    private val overlayChatPanelClosedListeners =
        java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    private val overlayReadListeners =
        java.util.concurrent.CopyOnWriteArrayList<(ChatRoomReadEvent) -> Unit>()
    private val overlayRoomUnreadListeners =
        java.util.concurrent.CopyOnWriteArrayList<(ChatRoomUnreadEvent) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun realtimeRoomIdsForPrimary(primaryRoomId: String): List<String> {
        val raid = chatRoomPreferences.getRaidRoomId()
        return buildList {
            add(primaryRoomId)
            if (!raid.isNullOrBlank() && raid != primaryRoomId) add(raid)
        }
    }

    private fun realtimeRoomIdsForOverlayBootstrap(): List<String> =
        ChatOverlayRoomIds.forOverlayBootstrap(
            raidRoomId = chatRoomPreferences.getRaidRoomId(),
            hubRoomId = chatRoomPreferences.getHubRoomId(),
            selectedRoomId = chatRoomPreferences.getSelectedRoomId(),
        )

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
        onRoomUnread: (ChatRoomUnreadEvent) -> Unit = {},
    ) {
        connectRealtimeRooms(
            roomIds = realtimeRoomIdsForPrimary(roomId),
            onMessage = onMessage,
            onDeleteMessage = onDeleteMessage,
            onTyping = onTyping,
            onRead = onRead,
            onRoomUnread = onRoomUnread,
        )
    }

    fun connectRealtimeRooms(
        roomIds: List<String>,
        onMessage: (ChatMessage) -> Unit,
        onDeleteMessage: (ChatMessageDeletedEvent) -> Unit = {},
        onTyping: (ChatTypingEvent) -> Unit = {},
        onRead: (ChatRoomReadEvent) -> Unit = {},
        onRoomUnread: (ChatRoomUnreadEvent) -> Unit = {},
    ) {
        val nextIds = roomIds.map { it.trim() }.filter { it.isNotEmpty() }
        val sameRooms = nextIds.size == primaryRealtimeRoomIds.size &&
            primaryRealtimeRoomIds.containsAll(nextIds)
        if (sameRooms && realtimeUiListener != null) {
            realtimeUiListener = onMessage
            realtimeDeleteListener = onDeleteMessage
            realtimeTypingListener = onTyping
            realtimeReadListener = onRead
            realtimeRoomUnreadListener = onRoomUnread
            return
        }
        realtimeUiListener?.let { socketManager.removeMessageListener(it) }
        realtimeDeleteListener?.let { socketManager.removeMessageDeletedListener(it) }
        realtimeTypingListener?.let { socketManager.removeTypingListener(it) }
        realtimeReadListener?.let { socketManager.removeReadListener(it) }
        realtimeRoomUnreadListener?.let { socketManager.removeRoomUnreadListener(it) }
        realtimeUiListener = onMessage
        realtimeDeleteListener = onDeleteMessage
        realtimeTypingListener = onTyping
        realtimeReadListener = onRead
        realtimeRoomUnreadListener = onRoomUnread
        socketManager.addMessageListener(onMessage)
        socketManager.addMessageDeletedListener(onDeleteMessage)
        socketManager.addTypingListener(onTyping)
        socketManager.addReadListener(onRead)
        socketManager.addRoomUnreadListener(onRoomUnread)
        primaryRealtimeRoomIds.clear()
        primaryRealtimeRoomIds.addAll(nextIds)
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
        refreshOverlayRealtimeSubscriptions()
    }

    fun removeOverlayReactionListener(listener: (OverlayReactionEvent) -> Unit) {
        overlayReactionListeners.remove(listener)
        socketManager.removeOverlayReactionListener(listener)
        if (overlayRealtimeListenersEmpty()) {
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
        realtimeRoomUnreadListener?.let { socketManager.removeRoomUnreadListener(it) }
        realtimeUiListener = null
        realtimeDeleteListener = null
        realtimeTypingListener = null
        realtimeReadListener = null
        realtimeRoomUnreadListener = null
        primaryRealtimeRoomIds.clear()
        if (overlayRealtimeRoomIds.isEmpty()) {
            socketManager.disconnect()
        } else {
            ensureRealtimeSocketConnected()
        }
    }

    fun addOverlayReadListener(listener: (ChatRoomReadEvent) -> Unit) {
        if (!overlayReadListeners.contains(listener)) {
            overlayReadListeners.add(listener)
        }
        socketManager.addReadListener(listener)
        refreshOverlayRealtimeSubscriptions()
    }

    fun removeOverlayReadListener(listener: (ChatRoomReadEvent) -> Unit) {
        overlayReadListeners.remove(listener)
        socketManager.removeReadListener(listener)
        if (overlayRealtimeListenersEmpty()) {
            overlayRealtimeRoomIds.clear()
            if (primaryRealtimeRoomIds.isEmpty()) {
                socketManager.disconnect()
            } else {
                ensureRealtimeSocketConnected()
            }
        }
    }

    private fun overlayRealtimeListenersEmpty(): Boolean =
        overlayMessageListeners.isEmpty() &&
            overlayReactionListeners.isEmpty() &&
            overlayReadListeners.isEmpty() &&
            overlayRoomUnreadListeners.isEmpty()

    fun addOverlayRoomUnreadListener(listener: (ChatRoomUnreadEvent) -> Unit) {
        if (!overlayRoomUnreadListeners.contains(listener)) {
            overlayRoomUnreadListeners.add(listener)
        }
        socketManager.addRoomUnreadListener(listener)
        refreshOverlayRealtimeSubscriptions()
    }

    fun removeOverlayRoomUnreadListener(listener: (ChatRoomUnreadEvent) -> Unit) {
        overlayRoomUnreadListeners.remove(listener)
        socketManager.removeRoomUnreadListener(listener)
        if (overlayRealtimeListenersEmpty()) {
            overlayRealtimeRoomIds.clear()
            if (primaryRealtimeRoomIds.isEmpty()) {
                socketManager.disconnect()
            } else {
                ensureRealtimeSocketConnected()
            }
        }
    }

    fun addOverlayMessageListener(listener: (ChatMessage) -> Unit) {
        if (!overlayMessageListeners.contains(listener)) {
            overlayMessageListeners.add(listener)
        }
        socketManager.addMessageListener(listener)
        refreshOverlayRealtimeSubscriptions()
    }

    /** Re-join raid/hub/selected rooms when overlay room prefs update after FGS already started. */
    fun refreshOverlayRealtimeSubscriptions() {
        if (overlayRealtimeListenersEmpty()) {
            return
        }
        overlayRealtimeRoomIds.clear()
        overlayRealtimeRoomIds.addAll(realtimeRoomIdsForOverlayBootstrap())
        ensureRealtimeSocketConnected()
    }

    fun removeOverlayMessageListener(listener: (ChatMessage) -> Unit) {
        overlayMessageListeners.remove(listener)
        socketManager.removeMessageListener(listener)
        if (overlayRealtimeListenersEmpty()) {
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
        overlayReadListeners.forEach { socketManager.removeReadListener(it) }
        overlayReadListeners.clear()
        overlayRoomUnreadListeners.forEach { socketManager.removeRoomUnreadListener(it) }
        overlayRoomUnreadListeners.clear()
        socketManager.disconnectSocketAndClearListeners()
    }

    fun overlayMessageListenerCount(): Int = overlayMessageListeners.size

    fun dispatchOverlayHttpMessage(message: ChatMessage) {
        val listeners = overlayMessageListeners.toList()
        if (listeners.isEmpty()) {
            CombatOverlayService.publishRaidMessageToStripFromApp(message)
            return
        }
        mainHandler.post {
            listeners.forEach { l -> runCatching { l(message) } }
        }
    }
}
