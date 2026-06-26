package com.lastasylum.alliance.data.chat

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.lastasylum.alliance.data.auth.JwtAccessTokenClaims
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.lastasylum.alliance.data.telemetry.DeliveryLatencyTracker
import com.lastasylum.alliance.data.telemetry.LatencySpanType
import org.json.JSONObject

class ChatSocketManager(
    private val latencyTracker: DeliveryLatencyTracker? = null,
) {
    private var socket: Socket? = null
    private var subscribedRoomIds: List<String> = emptyList()
    private var lastBaseUrl: String? = null
    private var tokenProvider: (() -> String?)? = null
    private val messageListeners = CopyOnWriteArrayList<(ChatMessage) -> Unit>()
    private val messageDeletedListeners =
        CopyOnWriteArrayList<(ChatMessageDeletedEvent) -> Unit>()
    private val typingListeners = CopyOnWriteArrayList<(ChatTypingEvent) -> Unit>()
    private val readListeners = CopyOnWriteArrayList<(ChatRoomReadEvent) -> Unit>()
    private val roomUnreadListeners = CopyOnWriteArrayList<(ChatRoomUnreadEvent) -> Unit>()
    private val roomPinChangedListeners =
        CopyOnWriteArrayList<(ChatRoomPinChangedEvent) -> Unit>()
    private val overlayReactionListeners =
        CopyOnWriteArrayList<(OverlayReactionEvent) -> Unit>()
    private val overlayReactionLogListeners =
        CopyOnWriteArrayList<(OverlayReactionLogEntryDto) -> Unit>()
    private val overlayReactionLogReactionListeners =
        CopyOnWriteArrayList<(OverlayReactionLogEntryDto) -> Unit>()
    private val chatHistoryClearedListeners = CopyOnWriteArrayList<() -> Unit>()
    private val outgoingMessageAckListeners =
        CopyOnWriteArrayList<(clientMessageId: String, message: ChatMessage) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectAttempt = 0
    private var intentionalDisconnect = false
    private var reconnectScheduled = false
    /**
     * True between [openSocket] and the next terminal connect/error/disconnect. Guards against the
     * reconnect storm: while a handshake is in flight, repeated [connect] calls (overlay + primary
     * subscription reconciliation) must only update the desired room set — never tear down and
     * reopen the socket, which on a slow handshake never converges to a stable connection.
     */
    @Volatile
    private var connectInFlight = false

    /**
     * Wall-clock instant the current handshake started (when [connectInFlight] flipped true). Used
     * by [isHandshakeStale] so a handshake that never produces a terminal connect/error/disconnect
     * event cannot latch [connectInFlight] forever: the next reopen path treats it as stale and
     * reconnects. This is the primary recovery — it does not depend on a Handler callback firing.
     */
    @Volatile
    private var connectInFlightSinceMs = 0L
    private var connectWatchdogScheduled = false
    private var staleTokenRefreshHandler: (() -> Unit)? = null

    private fun isHandshakeStale(): Boolean {
        if (!connectInFlight) return false
        val since = connectInFlightSinceMs
        if (since <= 0L) return false
        return System.currentTimeMillis() - since > CONNECT_STALE_MS
    }

    /**
     * Recovery for a stalled handshake: socket.io may start a connection that never produces a
     * terminal connect/error/disconnect event (observed against slow/cold-starting backends). In
     * that case [connectInFlight] would latch forever and every reopen path short-circuits, so the
     * socket never recovers and no realtime events arrive. The watchdog forcibly clears the latch
     * and reopens once the handshake has been in flight too long.
     */
    private val connectWatchdogRunnable = Runnable {
        connectWatchdogScheduled = false
        if (intentionalDisconnect) return@Runnable
        if (socket?.connected() == true) {
            connectInFlight = false
            return@Runnable
        }
        connectInFlight = false
        val base = lastBaseUrl
        val rooms = subscribedRoomIds
        val token = tokenProvider?.invoke()
        if (base != null && rooms.isNotEmpty() && token != null &&
            JwtAccessTokenClaims.isAccessTokenValid(token)
        ) {
            openSocket(base, token, rooms)
        } else {
            scheduleReconnect()
        }
    }

    private fun armConnectWatchdog() {
        cancelConnectWatchdog()
        connectWatchdogScheduled = true
        mainHandler.postDelayed(connectWatchdogRunnable, CONNECT_WATCHDOG_MS)
    }

    private fun cancelConnectWatchdog() {
        if (connectWatchdogScheduled) {
            mainHandler.removeCallbacks(connectWatchdogRunnable)
            connectWatchdogScheduled = false
        }
    }

    fun setStaleTokenRefreshHandler(handler: () -> Unit) {
        staleTokenRefreshHandler = handler
    }

    private val reconnectRunnable = Runnable {
        reconnectScheduled = false
        if (intentionalDisconnect) return@Runnable
        val base = lastBaseUrl ?: return@Runnable
        val rooms = subscribedRoomIds
        if (rooms.isEmpty()) return@Runnable
        val token = tokenProvider?.invoke() ?: return@Runnable
        if (!JwtAccessTokenClaims.isAccessTokenValid(token)) {
            staleTokenRefreshHandler?.invoke() ?: scheduleReconnect()
            return@Runnable
        }
        openSocket(base, token, rooms)
    }

    private val _connectionState = MutableStateFlow(ChatConnectionState.Disconnected)
    val connectionState: StateFlow<ChatConnectionState> = _connectionState.asStateFlow()

    private fun dispatchMessageOnMain(message: ChatMessage) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            messageListeners.forEach { l -> runCatching { l(message) } }
        } else {
            mainHandler.post {
                messageListeners.forEach { l -> runCatching { l(message) } }
            }
        }
    }

    private fun isSubscribedRoom(roomId: String): Boolean =
        roomId.isNotBlank() && subscribedRoomIds.contains(roomId)

    /** Join room traffic on connected socket (selected room switch, opportunistic ingest). */
    fun ensureRoomJoined(roomId: String) {
        val rid = roomId.trim()
        if (rid.isEmpty() || isSubscribedRoom(rid)) return
        subscribedRoomIds = (subscribedRoomIds + rid).distinct()
        socket?.takeIf { it.connected() }?.emit(
            "room:join",
            JSONObject().put("roomId", rid),
        )
    }

    private fun opportunisticallyJoinRoom(roomId: String) = ensureRoomJoined(roomId)

    fun addMessageListener(listener: (ChatMessage) -> Unit) {
        if (!messageListeners.contains(listener)) {
            messageListeners.add(listener)
        }
    }

    fun removeMessageListener(listener: (ChatMessage) -> Unit) {
        messageListeners.remove(listener)
    }

    fun addMessageDeletedListener(listener: (ChatMessageDeletedEvent) -> Unit) {
        if (!messageDeletedListeners.contains(listener)) {
            messageDeletedListeners.add(listener)
        }
    }

    fun removeMessageDeletedListener(listener: (ChatMessageDeletedEvent) -> Unit) {
        messageDeletedListeners.remove(listener)
    }

    fun addTypingListener(listener: (ChatTypingEvent) -> Unit) {
        if (!typingListeners.contains(listener)) {
            typingListeners.add(listener)
        }
    }

    fun removeTypingListener(listener: (ChatTypingEvent) -> Unit) {
        typingListeners.remove(listener)
    }

    fun addReadListener(listener: (ChatRoomReadEvent) -> Unit) {
        if (!readListeners.contains(listener)) {
            readListeners.add(listener)
        }
    }

    fun removeReadListener(listener: (ChatRoomReadEvent) -> Unit) {
        readListeners.remove(listener)
    }

    fun addRoomUnreadListener(listener: (ChatRoomUnreadEvent) -> Unit) {
        if (!roomUnreadListeners.contains(listener)) {
            roomUnreadListeners.add(listener)
        }
    }

    fun removeRoomUnreadListener(listener: (ChatRoomUnreadEvent) -> Unit) {
        roomUnreadListeners.remove(listener)
    }

    fun addRoomPinChangedListener(listener: (ChatRoomPinChangedEvent) -> Unit) {
        if (!roomPinChangedListeners.contains(listener)) {
            roomPinChangedListeners.add(listener)
        }
    }

    fun removeRoomPinChangedListener(listener: (ChatRoomPinChangedEvent) -> Unit) {
        roomPinChangedListeners.remove(listener)
    }

    fun addOverlayReactionListener(listener: (OverlayReactionEvent) -> Unit) {
        if (!overlayReactionListeners.contains(listener)) {
            overlayReactionListeners.add(listener)
        }
    }

    fun removeOverlayReactionListener(listener: (OverlayReactionEvent) -> Unit) {
        overlayReactionListeners.remove(listener)
    }

    fun addOverlayReactionLogListener(listener: (OverlayReactionLogEntryDto) -> Unit) {
        if (!overlayReactionLogListeners.contains(listener)) {
            overlayReactionLogListeners.add(listener)
        }
    }

    fun removeOverlayReactionLogListener(listener: (OverlayReactionLogEntryDto) -> Unit) {
        overlayReactionLogListeners.remove(listener)
    }

    fun addOverlayReactionLogReactionListener(listener: (OverlayReactionLogEntryDto) -> Unit) {
        if (!overlayReactionLogReactionListeners.contains(listener)) {
            overlayReactionLogReactionListeners.add(listener)
        }
    }

    fun removeOverlayReactionLogReactionListener(listener: (OverlayReactionLogEntryDto) -> Unit) {
        overlayReactionLogReactionListeners.remove(listener)
    }

    fun addChatHistoryClearedListener(listener: () -> Unit) {
        if (!chatHistoryClearedListeners.contains(listener)) {
            chatHistoryClearedListeners.add(listener)
        }
    }

    fun removeChatHistoryClearedListener(listener: () -> Unit) {
        chatHistoryClearedListeners.remove(listener)
    }

    fun clearMessageListeners() {
        messageListeners.clear()
        messageDeletedListeners.clear()
        typingListeners.clear()
        readListeners.clear()
        overlayReactionListeners.clear()
        overlayReactionLogListeners.clear()
        overlayReactionLogReactionListeners.clear()
        chatHistoryClearedListeners.clear()
    }

    fun emitOverlayReaction(
        targetUserId: String,
        reaction: String = "heart",
    ) {
        if (targetUserId.isBlank()) return
        val body = JSONObject()
            .put("targetUserId", targetUserId)
            .put("reaction", reaction)
        socket?.emit("overlay:reaction", body)
    }

    fun emitOverlayReactionReply(
        targetUserId: String,
        reaction: String,
        replyToLogId: String,
    ) {
        val parentId = replyToLogId.trim()
        if (targetUserId.isBlank() || parentId.isEmpty()) return
        val body = JSONObject()
            .put("targetUserId", targetUserId)
            .put("reaction", reaction)
            .put("replyToLogId", parentId)
        socket?.emit("overlay:reaction:reply", body)
    }

    /** One server-side fan-out to all teammates in game with overlay (single rate-limit slot). */
    fun emitOverlayReactionBroadcast(reaction: String = "heart") {
        socket?.emit(
            "overlay:reaction:broadcast",
            JSONObject().put("reaction", reaction),
        )
    }

    fun connect(
        baseUrl: String,
        roomIds: List<String>,
        tokenProvider: () -> String?,
    ) {
        intentionalDisconnect = false
        cancelReconnect()
        lastBaseUrl = baseUrl.trimEnd('/')
        this.tokenProvider = tokenProvider
        val distinct = roomIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val token = tokenProvider() ?: run {
            emitConnectionState(ChatConnectionState.Disconnected)
            return
        }
        if (distinct.isEmpty()) {
            emitConnectionState(ChatConnectionState.Disconnected)
            return
        }
        val existing = socket
        if (existing?.connected() == true && lastBaseUrl == baseUrl.trimEnd('/')) {
            val prev = subscribedRoomIds.toSet()
            val next = distinct.toSet()
            if (prev == next) {
                emitConnectionState(ChatConnectionState.Connected)
                return
            }
            for (rid in prev - next) {
                existing.emit(
                    "room:leave",
                    JSONObject().put("roomId", rid),
                )
            }
            for (rid in next - prev) {
                existing.emit(
                    "room:join",
                    JSONObject().put("roomId", rid),
                )
            }
            subscribedRoomIds = distinct
            emitConnectionState(ChatConnectionState.Connected)
            return
        }
        // Handshake already in flight (socket exists but not yet connected): only update the
        // desired rooms and let EVENT_CONNECT join them. Reopening here would restart the
        // handshake and, with frequent reconciliation calls, cause a reconnect storm. The
        // exception is a STALE handshake that never produced a terminal event — fall through so
        // openSocket can tear it down and reopen, otherwise the socket never recovers.
        if (existing != null && connectInFlight && !intentionalDisconnect && !isHandshakeStale()) {
            subscribedRoomIds = distinct
            return
        }
        subscribedRoomIds = distinct
        openSocket(lastBaseUrl!!, token, distinct)
    }

    fun reconnectWithFreshToken() {
        val rooms = subscribedRoomIds
        if (rooms.isEmpty()) return
        val base = lastBaseUrl ?: return
        val provider = tokenProvider ?: return
        val token = provider() ?: return
        intentionalDisconnect = false
        cancelReconnect()
        openSocket(base, token, rooms)
    }

    /** Foreground / overlay resume: drop backoff and reconnect immediately. */
    fun reconnectImmediatelyWithFreshToken() {
        intentionalDisconnect = false
        cancelReconnect()
        reconnectAttempt = 0
        reconnectWithFreshToken()
    }

    fun addOutgoingMessageAckListener(listener: (clientMessageId: String, message: ChatMessage) -> Unit) {
        outgoingMessageAckListeners.add(listener)
    }

    fun removeOutgoingMessageAckListener(listener: (clientMessageId: String, message: ChatMessage) -> Unit) {
        outgoingMessageAckListeners.remove(listener)
    }

    fun sendMessage(
        text: String,
        roomId: String,
        replyToMessageId: String? = null,
        clientMessageId: String? = null,
        gameEventAlert: String? = null,
    ) {
        val payload = JSONObject()
            .put("roomId", roomId)
            .put("text", text)
        if (!replyToMessageId.isNullOrBlank()) {
            payload.put("replyToMessageId", replyToMessageId)
        }
        val cid = clientMessageId?.trim()?.takeIf { it.isNotEmpty() }
        cid?.let { payload.put("clientMessageId", it) }
        gameEventAlert?.trim()?.takeIf { it.isNotEmpty() }?.let {
            payload.put("gameEventAlert", it)
        }
        val ack = Ack { args ->
            val parsed = parseMessageSentAck(args.firstOrNull()) ?: return@Ack
            val ackCid = parsed.clientMessageId?.trim()?.takeIf { it.isNotEmpty() }
                ?: cid
                ?: return@Ack
            cid?.let {
                latencyTracker?.endSpanByCorrelation(LatencySpanType.ChatSendToSocket, it, "ok")
            }
            dispatchOutgoingAckOnMain(ackCid, parsed)
        }
        // Только при ЖИВОМ соединении: иначе socket.io буферизует emit и при reconnect
        // отправит его повторно — сервер создаст ДУБЛЬ рядом с durable HTTP-отправкой outbox
        // (доставку и так гарантирует sendEnqueuedOutbox по тому же clientMessageId).
        val live = socket?.takeIf { it.connected() }
        if (live == null) {
            cid?.let {
                latencyTracker?.endSpanByCorrelation(LatencySpanType.ChatSendToSocket, it, "skipped_offline")
            }
            return
        }
        live.emit("message:send", payload, ack)
    }

    private fun dispatchOutgoingAckOnMain(clientMessageId: String, message: ChatMessage) {
        val listeners = outgoingMessageAckListeners.toList()
        if (listeners.isEmpty()) return
        mainHandler.post {
            listeners.forEach { listener ->
                runCatching { listener(clientMessageId, message) }
            }
        }
    }

    fun emitTyping(roomId: String) {
        if (roomId.isBlank()) return
        socket?.emit("typing", JSONObject().put("roomId", roomId))
    }

    fun disconnect() {
        intentionalDisconnect = true
        cancelReconnect()
        cancelConnectWatchdog()
        disconnectSocket()
    }

    fun disconnectSocketAndClearListeners() {
        intentionalDisconnect = true
        cancelReconnect()
        cancelConnectWatchdog()
        disconnectSocket()
        messageListeners.clear()
        messageDeletedListeners.clear()
        typingListeners.clear()
        readListeners.clear()
        roomUnreadListeners.clear()
        overlayReactionListeners.clear()
        overlayReactionLogListeners.clear()
        overlayReactionLogReactionListeners.clear()
        chatHistoryClearedListeners.clear()
        outgoingMessageAckListeners.clear()
    }

    private fun cancelReconnect() {
        mainHandler.removeCallbacks(reconnectRunnable)
        reconnectScheduled = false
    }

    private fun scheduleReconnect() {
        if (intentionalDisconnect || reconnectScheduled) return
        reconnectScheduled = true
        emitConnectionState(ChatConnectionState.Reconnecting)
        val attempt = reconnectAttempt++
        val delayMs = if (attempt < 3) {
            min(3_000L, (1L shl attempt) * 1_000L)
        } else {
            min(8_000L, (1L shl min(attempt, 4)) * 1_000L)
        }
        mainHandler.postDelayed(reconnectRunnable, delayMs)
    }

    private fun emitConnectionState(state: ChatConnectionState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _connectionState.value = state
        } else {
            mainHandler.post { _connectionState.value = state }
        }
    }

    private fun openSocket(baseUrl: String, accessToken: String, roomIds: List<String>) {
        // Central coalescing guard: every reopen path funnels through here (connect, reconnect
        // runnable, fresh-token reconnect). If a handshake is already in flight, or the socket is
        // already live to the same base, do NOT tear down and reopen — only update the desired
        // room set. Reopening mid-handshake is what caused the reconnect storm.
        val base = baseUrl.trimEnd('/')
        val stale = isHandshakeStale()
        if (!intentionalDisconnect && lastBaseUrl == base && !stale &&
            (connectInFlight || socket?.connected() == true)
        ) {
            subscribedRoomIds = roomIds
            socket?.takeIf { it.connected() }?.let { live ->
                for (rid in roomIds) {
                    live.emit("room:join", JSONObject().put("roomId", rid))
                }
            }
            return
        }
        emitConnectionState(ChatConnectionState.Connecting)
        disconnectSocketOnly()
        connectInFlight = true
        connectInFlightSinceMs = System.currentTimeMillis()
        armConnectWatchdog()

        try {
            val options = IO.Options.builder()
                .setPath("/socket.io/")
                .setAuth(mapOf("token" to accessToken))
                .setTimeout(CONNECT_HANDSHAKE_TIMEOUT_MS)
                .build()
            socket = IO.socket("$baseUrl/chat", options).apply {
                on(Socket.EVENT_CONNECT) {
                    reconnectAttempt = 0
                    connectInFlight = false
                    cancelConnectWatchdog()
                    emitConnectionState(ChatConnectionState.Connected)
                    // Join the latest desired set (may have been updated mid-handshake).
                    for (rid in subscribedRoomIds) {
                        emit(
                            "room:join",
                            JSONObject().put("roomId", rid),
                        )
                    }
                }
                on(Socket.EVENT_DISCONNECT) {
                    connectInFlight = false
                    cancelConnectWatchdog()
                    if (!intentionalDisconnect) {
                        scheduleReconnect()
                    } else {
                        emitConnectionState(ChatConnectionState.Disconnected)
                    }
                }
                on(Socket.EVENT_CONNECT_ERROR) {
                    connectInFlight = false
                    cancelConnectWatchdog()
                    if (!intentionalDisconnect) {
                        scheduleReconnect()
                    }
                }
                on("message:new") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val msgRoom = payload.optString("roomId", "")
                    if (msgRoom.isNotBlank() && !isSubscribedRoom(msgRoom)) {
                        opportunisticallyJoinRoom(msgRoom)
                    }
                    val message = ChatMessage(
                        _id = payload.optString("_id").takeIf { it.isNotBlank() },
                        allianceId = payload.optString(
                            "allianceId",
                            AllianceDefaults.DEFAULT_ALLIANCE_ID,
                        ),
                        roomId = msgRoom,
                        senderId = payload.optString("senderId"),
                        senderUsername = payload.optString("senderUsername"),
                        senderRole = payload.optString("senderRole"),
                        senderTeamTag = payload.optString("senderTeamTag")
                            .takeIf { it.isNotBlank() },
                        senderServerNumber = payload.optInt("senderServerNumber")
                            .takeIf { it > 0 },
                        senderAvatarRelativeUrl = payload.optString("senderAvatarRelativeUrl")
                            .takeIf { it.isNotBlank() },
                        text = payload.optString("text"),
                        editedAt = payload.optString("editedAt").takeIf { it.isNotBlank() },
                        forwardedFrom = payload.optJSONObject("forwardedFrom")?.toForwardedFrom(),
                        reactions = payload.optJSONArray("reactions")?.toReactions().orEmpty(),
                        createdAt = payload.optString("createdAt"),
                        updatedAt = payload.optString("updatedAt").takeIf { it.isNotBlank() },
                        replyToMessageId = payload.optString("replyToMessageId")
                            .takeIf { it.isNotBlank() },
                        replyTo = payload.optJSONObject("replyTo")?.toChatReplyPreview(),
                        deletedAt = payload.optString("deletedAt").takeIf { it.isNotBlank() },
                        deletedByUserId = payload.optString("deletedByUserId")
                            .takeIf { it.isNotBlank() },
                        attachments = payload.parseChatAttachments(),
                        clientMessageId = payload.optString("clientMessageId")
                            .trim()
                            .takeIf { it.isNotEmpty() },
                    )
                    payload.optString("clientMessageId").trim().takeIf { it.isNotEmpty() }?.let { cid ->
                        latencyTracker?.endSpanByCorrelation(LatencySpanType.ChatSendToSocket, cid, "ok")
                    }
                    dispatchMessageOnMain(message)
                }
                on("message:edited") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val msgRoom = payload.optString("roomId", "")
                    if (msgRoom.isNotBlank() && !isSubscribedRoom(msgRoom)) {
                        opportunisticallyJoinRoom(msgRoom)
                    }
                    val message = payload.toChatMessage()
                    dispatchMessageOnMain(message)
                }
                on("message:reaction") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val msgRoom = payload.optString("roomId", "")
                    if (msgRoom.isNotBlank() && !isSubscribedRoom(msgRoom)) {
                        opportunisticallyJoinRoom(msgRoom)
                    }
                    val viewerUserId = JwtAccessTokenClaims.sub(tokenProvider?.invoke())
                    val message = payload.toReactionSocketMessage(viewerUserId)
                    dispatchMessageOnMain(message)
                }
                on("message:deleted") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val event = ChatMessageDeletedEvent(
                        messageId = payload.optString("messageId"),
                        roomId = payload.optString("roomId"),
                        deletedAt = payload.optString("deletedAt").takeIf { it.isNotBlank() },
                        deletedByUserId = payload.optString("deletedByUserId")
                            .takeIf { it.isNotBlank() },
                    )
                    if (event.roomId.isNotBlank() && !isSubscribedRoom(event.roomId)) {
                        opportunisticallyJoinRoom(event.roomId)
                    }
                    if (event.messageId.isBlank()) return@on
                    messageDeletedListeners.forEach { l ->
                        runCatching { l(event) }
                    }
                }
                on("user:typing") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val rid = payload.optString("roomId", "")
                    if (rid.isNotBlank() && !isSubscribedRoom(rid)) {
                        opportunisticallyJoinRoom(rid)
                    }
                    val event = ChatTypingEvent(
                        roomId = rid,
                        userId = payload.optString("userId"),
                        username = payload.optString("username"),
                    )
                    if (event.userId.isBlank()) return@on
                    typingListeners.forEach { l ->
                        runCatching { l(event) }
                    }
                }
                on("room:read") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val rid = payload.optString("roomId", "")
                    if (rid.isNotBlank() && !isSubscribedRoom(rid)) {
                        opportunisticallyJoinRoom(rid)
                    }
                    val event = ChatRoomReadEvent(
                        roomId = rid,
                        userId = payload.optString("userId"),
                        messageId = payload.optString("messageId"),
                    )
                    if (event.userId.isBlank() || event.messageId.isBlank()) return@on
                    if (com.lastasylum.alliance.BuildConfig.DEBUG) {
                        android.util.Log.d(
                            "ChatReadReceipt",
                            "socket room:read user=${event.userId} room=${event.roomId} upto=${event.messageId}",
                        )
                    }
                    readListeners.forEach { l -> runCatching { l(event) } }
                }
                on("rooms:unread") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val rid = payload.optString("roomId", "")
                    if (rid.isBlank()) return@on
                    val event = ChatRoomUnreadEvent(
                        roomId = rid,
                        unreadCount = payload.optInt("unreadCount", 0).coerceAtLeast(0),
                        lastReadMessageId = payload.optString("lastReadMessageId")
                            .takeIf { it.isNotBlank() },
                    )
                    roomUnreadListeners.forEach { l -> runCatching { l(event) } }
                }
                on("room:pin-changed") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val event = payload.toChatRoomPinChangedEvent() ?: return@on
                    val rid = event.roomId
                    if (rid.isNotBlank() && !isSubscribedRoom(rid)) {
                        opportunisticallyJoinRoom(rid)
                    }
                    roomPinChangedListeners.forEach { l -> runCatching { l(event) } }
                }
                val overlayReactionHandler: (Array<out Any>) -> Unit = reactionHandler@{ args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@reactionHandler
                    val fromUserId = payload.optString("fromUserId", "")
                    val fromUsername = payload.optString("fromUsername", "")
                    val targetUserId = payload.optString("targetUserId", "")
                    val reaction = payload.optString("reaction", "heart")
                        .ifBlank { "heart" }
                    if (fromUserId.isBlank() || targetUserId.isBlank()) return@reactionHandler
                    val event = OverlayReactionEvent(
                        fromUserId = fromUserId,
                        fromUsername = fromUsername,
                        reaction = reaction,
                        targetUserId = targetUserId,
                        broadcast = payload.optBoolean("broadcast", false),
                        logEntryId = payload.optString("logEntryId").takeIf { it.isNotBlank() },
                        replyToLogId = payload.optString("replyToLogId").takeIf { it.isNotBlank() },
                        replyToLog = payload.optJSONObject("replyToLog")?.toOverlayReactionBurstReplyTo(),
                    )
                    overlayReactionListeners.forEach { l -> runCatching { l(event) } }
                }
                on("overlay:reaction", overlayReactionHandler)
                on("overlay:reaction:reply", overlayReactionHandler)
                on("overlay:reaction:log") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val logEntry = payload.optJSONObject("logEntry") ?: return@on
                    val dto = logEntry.toOverlayReactionLogEntryDto() ?: return@on
                    overlayReactionLogListeners.forEach { l -> runCatching { l(dto) } }
                }
                on("overlay:reaction:log:reaction") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val logEntry = payload.optJSONObject("logEntry") ?: return@on
                    val dto = logEntry.toOverlayReactionLogEntryDto() ?: return@on
                    overlayReactionLogReactionListeners.forEach { l -> runCatching { l(dto) } }
                }
                on("chat:history:cleared") {
                    val listeners = chatHistoryClearedListeners.toList()
                    if (listeners.isEmpty()) return@on
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        listeners.forEach { l -> runCatching { l() } }
                    } else {
                        mainHandler.post { listeners.forEach { l -> runCatching { l() } } }
                    }
                }
                connect()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Socket setup failed: $baseUrl/chat", e)
            connectInFlight = false
            cancelConnectWatchdog()
            disconnectSocketOnly()
            emitConnectionState(ChatConnectionState.Disconnected)
            if (!intentionalDisconnect) {
                scheduleReconnect()
            }
        }
    }

    private fun disconnectSocketOnly() {
        socket?.disconnect()
        socket?.off()
        socket = null
        // Clear the in-flight latch on every teardown. Otherwise a disconnect that happens while a
        // handshake is still in flight (socket nulled, no terminal connect/error event) leaves
        // connectInFlight=true forever, and every subsequent openSocket short-circuits to
        // SKIP-reopen against a null socket — the socket can never reconnect. openSocket calls this
        // immediately before setting connectInFlight=true again, so the reopen path is unaffected.
        connectInFlight = false
        connectInFlightSinceMs = 0L
    }

    private fun disconnectSocket() {
        disconnectSocketOnly()
        subscribedRoomIds = emptyList()
        lastBaseUrl = null
        tokenProvider = null
        emitConnectionState(ChatConnectionState.Disconnected)
    }

    private companion object {
        private const val TAG = "ChatSocket"

        /** socket.io transport/handshake timeout — bounds how long one attempt may hang. */
        private const val CONNECT_HANDSHAKE_TIMEOUT_MS = 20_000L

        /**
         * After this long without the handshake reaching a terminal connect/error/disconnect event,
         * an in-flight handshake is considered stale and the next reopen path tears it down and
         * reconnects instead of short-circuiting. Set just above the handshake timeout so a normal
         * (even slow) connect is never pre-empted, while a silently stuck socket still recovers.
         */
        private const val CONNECT_STALE_MS = 22_000L

        /**
         * Wall-clock watchdog over [connectInFlight]. Slightly longer than the handshake timeout so
         * it only fires when socket.io fails to surface a terminal connect/error/disconnect event at
         * all, then forces a clean reopen so the in-flight latch cannot stick forever.
         */
        private const val CONNECT_WATCHDOG_MS = 25_000L
    }
}

private fun JSONObject.toOverlayReactionLogEntryDto(): OverlayReactionLogEntryDto? {
    val entryId = optString("_id").takeIf { it.isNotBlank() }
        ?: optString("id").takeIf { it.isNotBlank() }
        ?: return null
    val senderUserId = optString("senderUserId", "").trim()
    if (senderUserId.isEmpty()) return null
    return OverlayReactionLogEntryDto(
        _id = entryId,
        senderUserId = senderUserId,
        senderUsername = optString("senderUsername", ""),
        targetUserId = optString("targetUserId").takeIf { it.isNotBlank() },
        targetUsername = optString("targetUsername").takeIf { it.isNotBlank() },
        reaction = optString("reaction", "heart").ifBlank { "heart" },
        visibility = optString("visibility", "personal"),
        createdAt = optString("createdAt", ""),
        reactions = optJSONArray("reactions")?.let { reactionsArray ->
            (0 until reactionsArray.length()).mapNotNull { index ->
                val reactionObj = reactionsArray.optJSONObject(index) ?: return@mapNotNull null
                val emoji = reactionObj.optString("emoji").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ChatReactionDto(
                    emoji = emoji,
                    count = reactionObj.optInt("count", 0),
                    reactedByMe = reactionObj.optBoolean("reactedByMe", false),
                )
            }
        },
        replyToLogId = optString("replyToLogId").takeIf { it.isNotBlank() },
        replyToLog = optJSONObject("replyToLog")?.toOverlayReactionLogReplyToDto(),
    )
}

private fun JSONObject.toOverlayReactionBurstReplyTo(): OverlayReactionBurstReplyTo? {
    val logId = optString("_id").takeIf { it.isNotBlank() }
        ?: optString("id").takeIf { it.isNotBlank() }
        ?: return null
    return OverlayReactionBurstReplyTo(
        logId = logId,
        reaction = optString("reaction", "heart").ifBlank { "heart" },
        visibility = OverlayReactionLogVisibility.fromWire(optString("visibility", "personal")),
    )
}

private fun JSONObject.toOverlayReactionLogReplyToDto(): OverlayReactionLogReplyToDto? {
    val logId = optString("_id").takeIf { it.isNotBlank() }
        ?: optString("id").takeIf { it.isNotBlank() }
        ?: return null
    val senderUserId = optString("senderUserId", "").trim()
    return OverlayReactionLogReplyToDto(
        _id = logId,
        reaction = optString("reaction", "heart").ifBlank { "heart" },
        visibility = optString("visibility", "personal"),
        senderUserId = senderUserId,
        senderUsername = optString("senderUsername", ""),
        targetUserId = optString("targetUserId").takeIf { it.isNotBlank() },
        targetUsername = optString("targetUsername").takeIf { it.isNotBlank() },
    )
}

private fun JSONObject.toChatReplyPreview(): ChatMessageReplyPreview? {
    val id = optString("_id").takeIf { it.isNotBlank() } ?: return null
    return ChatMessageReplyPreview(
        _id = id,
        senderId = optString("senderId"),
        senderUsername = optString("senderUsername"),
        senderRole = optString("senderRole"),
        senderTeamTag = optString("senderTeamTag").takeIf { it.isNotBlank() },
        senderServerNumber = optInt("senderServerNumber").takeIf { it > 0 },
        text = optString("text"),
        createdAt = optString("createdAt").takeIf { it.isNotBlank() },
        deletedAt = optString("deletedAt").takeIf { it.isNotBlank() },
    )
}

private fun JSONObject.toForwardedFrom(): ChatForwardedFrom? {
    val messageId = optString("messageId").takeIf { it.isNotBlank() } ?: return null
    return ChatForwardedFrom(
        messageId = messageId,
        senderId = optString("senderId"),
        senderUsername = optString("senderUsername"),
        senderRole = optString("senderRole"),
        senderTeamTag = optString("senderTeamTag").takeIf { it.isNotBlank() },
        senderServerNumber = optInt("senderServerNumber").takeIf { it > 0 },
    )
}

private fun org.json.JSONArray.toReactions(viewerUserId: String? = null): List<ChatReaction> {
    val out = ArrayList<ChatReaction>(length())
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        val emoji = o.optString("emoji").takeIf { it.isNotBlank() } ?: continue
        val userIds = o.optJSONArray("userIds")
        val count = when {
            o.has("count") -> o.optInt("count", 0)
            userIds != null && userIds.length() > 0 -> userIds.length()
            else -> 0
        }
        val reactedByMe = when {
            o.has("reactedByMe") -> o.optBoolean("reactedByMe", false)
            !viewerUserId.isNullOrBlank() && userIds != null -> {
                (0 until userIds.length()).any { userIds.optString(it) == viewerUserId }
            }
            else -> false
        }
        out.add(
            ChatReaction(
                emoji = emoji,
                count = count,
                reactedByMe = reactedByMe,
            ),
        )
    }
    return out
}

/** Neutral backend payload or legacy full message row. */
private fun JSONObject.toReactionSocketMessage(viewerUserId: String? = null): ChatMessage {
    val compactId = optString("messageId").takeIf { it.isNotBlank() }
    return if (compactId != null && !has("senderId")) {
        ChatMessage(
            _id = compactId,
            allianceId = "",
            roomId = optString("roomId"),
            senderId = "",
            senderUsername = "",
            senderRole = "",
            reactions = optJSONArray("reactions")?.toReactions(viewerUserId).orEmpty(),
            text = "",
            createdAt = "",
        )
    } else {
        toChatMessage()
    }
}

private fun JSONObject.toChatMessage(): ChatMessage {
    val msgRoom = optString("roomId", "")
    return ChatMessage(
        _id = optString("_id").takeIf { it.isNotBlank() },
        allianceId = optString("allianceId", AllianceDefaults.DEFAULT_ALLIANCE_ID),
        roomId = msgRoom,
        senderId = optString("senderId"),
        senderUsername = optString("senderUsername"),
        senderRole = optString("senderRole"),
        senderTeamTag = optString("senderTeamTag").takeIf { it.isNotBlank() },
        senderServerNumber = optInt("senderServerNumber").takeIf { it > 0 },
        senderAvatarRelativeUrl = optString("senderAvatarRelativeUrl").takeIf { it.isNotBlank() },
        text = optString("text"),
        editedAt = optString("editedAt").takeIf { it.isNotBlank() },
        forwardedFrom = optJSONObject("forwardedFrom")?.toForwardedFrom(),
        reactions = optJSONArray("reactions")?.toReactions().orEmpty(),
        createdAt = optString("createdAt"),
        updatedAt = optString("updatedAt").takeIf { it.isNotBlank() },
        replyToMessageId = optString("replyToMessageId").takeIf { it.isNotBlank() },
        replyTo = optJSONObject("replyTo")?.toChatReplyPreview(),
        deletedAt = optString("deletedAt").takeIf { it.isNotBlank() },
        deletedByUserId = optString("deletedByUserId").takeIf { it.isNotBlank() },
        attachments = parseChatAttachments(),
        clientMessageId = optString("clientMessageId").trim().takeIf { it.isNotEmpty() },
    )
}

/** NestJS WS handler return: `{ event: 'message:sent', data: ChatMessage }`. */
internal fun parseMessageSentAck(raw: Any?): ChatMessage? {
    val root = raw as? JSONObject ?: return null
    val data = root.optJSONObject("data") ?: root
    if (data.has("_id") || data.has("roomId")) {
        return data.toChatMessage()
    }
    return null
}

private fun JSONObject.toPinnedMessagePreviewDto(): PinnedMessagePreviewDto? {
    val id = optString("id").ifBlank { optString("_id") }
    if (id.isBlank()) return null
    return PinnedMessagePreviewDto(
        id = id,
        text = optString("text"),
        senderUsername = optString("senderUsername"),
        senderTeamTag = optString("senderTeamTag").takeIf { it.isNotBlank() },
        senderServerNumber = optInt("senderServerNumber").takeIf { it > 0 },
        createdAt = optString("createdAt"),
        editedAt = optString("editedAt").takeIf { it.isNotBlank() },
        hasImage = optBoolean("hasImage", false),
        isSticker = optBoolean("isSticker", false),
        imageThumbnailUrl = optString("imageThumbnailUrl").takeIf { it.isNotBlank() },
        pinnedByUsername = optString("pinnedByUsername").takeIf { it.isNotBlank() },
    )
}

private fun JSONObject.parsePinnedMessagesArray(): List<PinnedMessagePreviewDto> {
    val arr = optJSONArray("pinnedMessages") ?: return emptyList()
    val out = ArrayList<PinnedMessagePreviewDto>(arr.length())
    for (i in 0 until arr.length()) {
        arr.optJSONObject(i)?.toPinnedMessagePreviewDto()?.let { out.add(it) }
    }
    return out
}

private fun JSONObject.toChatRoomPinChangedEvent(): ChatRoomPinChangedEvent? {
    val roomId = optString("roomId").ifBlank { return null }
    return ChatRoomPinChangedEvent(
        roomId = roomId,
        pinnedMessageId = optString("pinnedMessageId").takeIf { it.isNotBlank() },
        pinnedAt = optString("pinnedAt").takeIf { it.isNotBlank() },
        pinnedByUserId = optString("pinnedByUserId").takeIf { it.isNotBlank() },
        pinnedMessage = optJSONObject("pinnedMessage")?.toPinnedMessagePreviewDto(),
        pinnedMessages = parsePinnedMessagesArray(),
    )
}
