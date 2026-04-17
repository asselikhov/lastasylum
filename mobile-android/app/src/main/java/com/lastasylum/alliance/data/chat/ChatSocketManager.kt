package com.lastasylum.alliance.data.chat

import android.os.Handler
import android.os.Looper
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

    fun clearMessageListeners() {
        messageListeners.clear()
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
            _connectionState.value = ChatConnectionState.Disconnected
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

    fun sendMessage(text: String, roomId: String) {
        socket?.emit(
            "message:send",
            JSONObject()
                .put("roomId", roomId)
                .put("text", text),
        )
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
    }

    private fun cancelReconnect() {
        mainHandler.removeCallbacks(reconnectRunnable)
        reconnectScheduled = false
    }

    private fun scheduleReconnect() {
        if (intentionalDisconnect || reconnectScheduled) return
        reconnectScheduled = true
        _connectionState.value = ChatConnectionState.Reconnecting
        val attempt = reconnectAttempt++
        val delayMs = min(30_000L, (1L shl min(attempt, 5)) * 1000L)
        mainHandler.postDelayed(reconnectRunnable, delayMs)
    }

    private fun openSocket(baseUrl: String, accessToken: String, roomId: String) {
        _connectionState.value = ChatConnectionState.Connecting
        disconnectSocketOnly()

        val options = IO.Options.builder()
            .setPath("/socket.io/")
            .setAuth(mapOf("token" to accessToken))
            .build()
        socket = IO.socket("$baseUrl/chat", options).apply {
            on(Socket.EVENT_CONNECT) {
                reconnectAttempt = 0
                _connectionState.value = ChatConnectionState.Connected
                emit(
                    "room:join",
                    JSONObject().put("roomId", roomId),
                )
            }
            on(Socket.EVENT_DISCONNECT) {
                if (!intentionalDisconnect) {
                    scheduleReconnect()
                } else {
                    _connectionState.value = ChatConnectionState.Disconnected
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
                    text = payload.optString("text"),
                    createdAt = payload.optString("createdAt"),
                )
                messageListeners.forEach { l ->
                    runCatching { l(message) }
                }
            }
            connect()
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
        _connectionState.value = ChatConnectionState.Disconnected
    }
}
