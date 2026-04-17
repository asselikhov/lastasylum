package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.data.auth.TokenStore
import kotlinx.coroutines.flow.StateFlow

class ChatRepository(
    private val chatApi: ChatApi,
    private val tokenStore: TokenStore,
    private val socketManager: ChatSocketManager,
    private val chatRoomPreferences: ChatRoomPreferences,
) {
    private var realtimeUiListener: ((ChatMessage) -> Unit)? = null

    val realtimeConnectionState: StateFlow<ChatConnectionState> =
        socketManager.connectionState

    suspend fun listRooms(): Result<List<ChatRoomDto>> =
        runCatching { chatApi.listRooms() }

    suspend fun loadRecentMessages(
        roomId: String,
        beforeMessageId: String? = null,
        limit: Int = 30,
    ): Result<List<ChatMessage>> {
        return runCatching {
            chatApi.getMessages(roomId = roomId, before = beforeMessageId, limit = limit)
        }
    }

    suspend fun sendMessage(text: String, roomId: String): Result<ChatMessage> {
        return runCatching {
            chatApi.sendMessage(
                SendMessageRequest(text = text, roomId = roomId),
            )
        }
    }

    suspend fun sendSystemVoiceMessage(text: String): Result<ChatMessage> {
        val roomId = chatRoomPreferences.getSelectedRoomId()
            ?: return Result.failure(IllegalStateException("no_room"))
        return runCatching {
            chatApi.sendMessage(SendMessageRequest(text = text.trim(), roomId = roomId))
        }
    }

    fun connectRealtime(roomId: String, onMessage: (ChatMessage) -> Unit) {
        realtimeUiListener?.let { socketManager.removeMessageListener(it) }
        realtimeUiListener = onMessage
        socketManager.addMessageListener(onMessage)
        socketManager.connect(
            baseUrl = BuildConfig.API_BASE_URL,
            roomId = roomId,
            tokenProvider = { tokenStore.getAccessToken() },
        )
    }

    /** Called after OkHttp refreshes access token so the socket re-authenticates. */
    fun onAccessTokenRefreshed() {
        socketManager.reconnectWithFreshToken()
    }

    fun disconnectRealtime() {
        realtimeUiListener?.let { socketManager.removeMessageListener(it) }
        realtimeUiListener = null
        socketManager.disconnect()
    }

    fun addOverlayMessageListener(listener: (ChatMessage) -> Unit) {
        socketManager.addMessageListener(listener)
        val roomId = chatRoomPreferences.getSelectedRoomId() ?: return
        socketManager.connect(
            baseUrl = BuildConfig.API_BASE_URL,
            roomId = roomId,
            tokenProvider = { tokenStore.getAccessToken() },
        )
    }

    fun removeOverlayMessageListener(listener: (ChatMessage) -> Unit) {
        socketManager.removeMessageListener(listener)
    }

    fun resetRealtimeForLogout() {
        realtimeUiListener?.let { socketManager.removeMessageListener(it) }
        realtimeUiListener = null
        socketManager.disconnectSocketAndClearListeners()
    }
}
