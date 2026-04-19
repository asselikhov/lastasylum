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
    private var subscribedRoomId: String? = null
    private var lastBaseUrl: String? = null
    private var tokenProvider: (() -> String?)? = null
    private val messageListeners = CopyOnWriteArrayList<(ChatMessage) -> Unit>()
    private val messageDeletedListeners =
        CopyOnWriteArrayList<(ChatMessageDeletedEvent) -> Unit>()
    private val typingListeners = CopyOnWriteArrayList<(ChatTypingEvent) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectAttempt = 0
    private var intentionalDisconnect = false
    private var reconnectScheduled = false

    private val reconnectRunnable = Runnable {
        reconnectScheduled = false
        if (intentionalDisconnect) return@Runnable
        val base = lastBaseUrl ?: return@Runnable
        val room = subscribedRoomId ?: return@Runnable
        val token = tokenProvider?.invoke() ?: return@Runnable
        openSocket(base, token, room)
    }

    private val _connectionState = MutableStateFlow(ChatConnectionState.Disconnected)
    val connectionState: StateFlow<ChatConnectionState> = _connectionState.asStateFlow()

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

    fun clearMessageListeners() {
        messageListeners.clear()
        messageDeletedListeners.clear()
        typingListeners.clear()
    }

    fun connect(
        baseUrl: String,
        roomId: String,
        tokenProvider: () -> String?,
    ) {
        intentionalDisconnect = false
        cancelReconnect()
        lastBaseUrl = baseUrl.trimEnd('/')
        this.tokenProvider = tokenProvider
        val token = tokenProvider() ?: run {
            emitConnectionState(ChatConnectionState.Disconnected)
            return
        }
        if (socket?.connected() == true && subscribedRoomId == roomId) {
            return
        }
        subscribedRoomId = roomId
        openSocket(lastBaseUrl!!, token, roomId)
    }

    fun reconnectWithFreshToken() {
        val room = subscribedRoomId ?: return
        val base = lastBaseUrl ?: return
        val provider = tokenProvider ?: return
        val token = provider() ?: return
        intentionalDisconnect = false
        cancelReconnect()
        openSocket(base, token, room)
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
        val delayMs = min(30_000L, (1L shl min(attempt, 5)) * 1000L)
        mainHandler.postDelayed(reconnectRunnable, delayMs)
    }

    private fun emitConnectionState(state: ChatConnectionState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _connectionState.value = state
        } else {
            mainHandler.post { _connectionState.value = state }
        }
    }

    private fun openSocket(baseUrl: String, accessToken: String, roomId: String) {
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
                    emit(
                        "room:join",
                        JSONObject().put("roomId", roomId),
                    )
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
                    if (msgRoom.isNotBlank() && msgRoom != roomId) return@on
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
                        senderTelegramUsername = payload.optString("senderTelegramUsername")
                            .takeIf { it.isNotBlank() },
                        text = payload.optString("text"),
                        createdAt = payload.optString("createdAt"),
                        updatedAt = payload.optString("updatedAt").takeIf { it.isNotBlank() },
                        replyToMessageId = payload.optString("replyToMessageId")
                            .takeIf { it.isNotBlank() },
                        replyTo = payload.optJSONObject("replyTo")?.toChatReplyPreview(),
                        deletedAt = payload.optString("deletedAt").takeIf { it.isNotBlank() },
                        deletedByUserId = payload.optString("deletedByUserId")
                            .takeIf { it.isNotBlank() },
                    )
                    messageListeners.forEach { l ->
                        runCatching { l(message) }
                    }
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
                    if (event.roomId.isNotBlank() && event.roomId != roomId) return@on
                    if (event.messageId.isBlank()) return@on
                    messageDeletedListeners.forEach { l ->
                        runCatching { l(event) }
                    }
                }
                on("user:typing") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val rid = payload.optString("roomId", "")
                    if (rid.isNotBlank() && rid != roomId) return@on
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
        subscribedRoomId = null
        lastBaseUrl = null
        tokenProvider = null
        emitConnectionState(ChatConnectionState.Disconnected)
    }

    private companion object {
        private const val TAG = "ChatSocket"
    }
}

private fun JSONObject.toChatReplyPreview(): ChatMessageReplyPreview? {
    val id = optString("_id").takeIf { it.isNotBlank() } ?: return null
    return ChatMessageReplyPreview(
        _id = id,
        senderId = optString("senderId"),
        senderUsername = optString("senderUsername"),
        senderRole = optString("senderRole"),
        senderTeamTag = optString("senderTeamTag").takeIf { it.isNotBlank() },
        text = optString("text"),
        createdAt = optString("createdAt").takeIf { it.isNotBlank() },
        deletedAt = optString("deletedAt").takeIf { it.isNotBlank() },
    )
}
