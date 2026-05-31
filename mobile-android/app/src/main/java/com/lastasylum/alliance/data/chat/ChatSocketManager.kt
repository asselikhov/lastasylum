package com.lastasylum.alliance.data.chat

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class ChatSocketManager {
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
    private val overlayReactionListeners =
        CopyOnWriteArrayList<(OverlayReactionEvent) -> Unit>()
    private val overlayReactionLogListeners =
        CopyOnWriteArrayList<(OverlayReactionLogEntryDto) -> Unit>()
    private val chatHistoryClearedListeners = CopyOnWriteArrayList<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectAttempt = 0
    private var intentionalDisconnect = false
    private var reconnectScheduled = false

    private val reconnectRunnable = Runnable {
        reconnectScheduled = false
        if (intentionalDisconnect) return@Runnable
        val base = lastBaseUrl ?: return@Runnable
        val rooms = subscribedRoomIds
        if (rooms.isEmpty()) return@Runnable
        val token = tokenProvider?.invoke() ?: return@Runnable
        openSocket(base, token, rooms)
    }

    private val _connectionState = MutableStateFlow(ChatConnectionState.Disconnected)
    val connectionState: StateFlow<ChatConnectionState> = _connectionState.asStateFlow()

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
        chatHistoryClearedListeners.clear()
    }

    fun emitOverlayReaction(targetUserId: String, reaction: String = "heart") {
        if (targetUserId.isBlank()) return
        socket?.emit(
            "overlay:reaction",
            JSONObject()
                .put("targetUserId", targetUserId)
                .put("reaction", reaction),
        )
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

    fun sendMessage(text: String, roomId: String, replyToMessageId: String? = null) {
        socket?.emit(
            "message:send",
            JSONObject()
                .put("roomId", roomId)
                .put("text", text)
                .put("replyToMessageId", replyToMessageId),
        )
    }

    fun emitTyping(roomId: String) {
        if (roomId.isBlank()) return
        socket?.emit("typing", JSONObject().put("roomId", roomId))
    }

    fun disconnect() {
        intentionalDisconnect = true
        cancelReconnect()
        disconnectSocket()
    }

    fun disconnectSocketAndClearListeners() {
        intentionalDisconnect = true
        cancelReconnect()
        disconnectSocket()
        messageListeners.clear()
        messageDeletedListeners.clear()
        typingListeners.clear()
        readListeners.clear()
        roomUnreadListeners.clear()
        overlayReactionListeners.clear()
        overlayReactionLogListeners.clear()
        chatHistoryClearedListeners.clear()
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
        val delayMs = min(8_000L, (1L shl min(attempt, 4)) * 1000L)
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
        emitConnectionState(ChatConnectionState.Connecting)
        disconnectSocketOnly()

        try {
            val options = IO.Options.builder()
                .setPath("/socket.io/")
                .setAuth(mapOf("token" to accessToken))
                .build()
            socket = IO.socket("$baseUrl/chat", options).apply {
                on(Socket.EVENT_CONNECT) {
                    reconnectAttempt = 0
                    emitConnectionState(ChatConnectionState.Connected)
                    for (rid in roomIds) {
                        emit(
                            "room:join",
                            JSONObject().put("roomId", rid),
                        )
                    }
                }
                on(Socket.EVENT_DISCONNECT) {
                    if (!intentionalDisconnect) {
                        scheduleReconnect()
                    } else {
                        emitConnectionState(ChatConnectionState.Disconnected)
                    }
                }
                on(Socket.EVENT_CONNECT_ERROR) {
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
                        senderTelegramUsername = payload.optString("senderTelegramUsername")
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
                    )
                    messageListeners.forEach { l ->
                        runCatching { l(message) }
                    }
                }
                on("message:edited") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val msgRoom = payload.optString("roomId", "")
                    if (msgRoom.isNotBlank() && !isSubscribedRoom(msgRoom)) {
                        opportunisticallyJoinRoom(msgRoom)
                    }
                    val message = payload.toChatMessage()
                    messageListeners.forEach { l -> runCatching { l(message) } }
                }
                on("message:reaction") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val msgRoom = payload.optString("roomId", "")
                    if (msgRoom.isNotBlank() && !isSubscribedRoom(msgRoom)) {
                        opportunisticallyJoinRoom(msgRoom)
                    }
                    val message = payload.toReactionSocketMessage()
                    messageListeners.forEach { l -> runCatching { l(message) } }
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
                on("overlay:reaction") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val fromUserId = payload.optString("fromUserId", "")
                    val fromUsername = payload.optString("fromUsername", "")
                    val targetUserId = payload.optString("targetUserId", "")
                    val reaction = payload.optString("reaction", "heart")
                        .ifBlank { "heart" }
                    if (fromUserId.isBlank() || targetUserId.isBlank()) return@on
                    val event = OverlayReactionEvent(
                        fromUserId = fromUserId,
                        fromUsername = fromUsername,
                        reaction = reaction,
                        targetUserId = targetUserId,
                        broadcast = payload.optBoolean("broadcast", false),
                    )
                    overlayReactionListeners.forEach { l -> runCatching { l(event) } }
                }
                on("overlay:reaction:log") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val logEntry = payload.optJSONObject("logEntry") ?: return@on
                    val dto = logEntry.toOverlayReactionLogEntryDto() ?: return@on
                    overlayReactionLogListeners.forEach { l -> runCatching { l(dto) } }
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
private fun JSONObject.toReactionSocketMessage(): ChatMessage {
    val compactId = optString("messageId").takeIf { it.isNotBlank() }
    return if (compactId != null && !has("senderId")) {
        ChatMessage(
            _id = compactId,
            allianceId = "",
            roomId = optString("roomId"),
            senderId = "",
            senderUsername = "",
            senderRole = "",
            reactions = optJSONArray("reactions")?.toReactions().orEmpty(),
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
        senderTelegramUsername = optString("senderTelegramUsername").takeIf { it.isNotBlank() },
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
    )
}
