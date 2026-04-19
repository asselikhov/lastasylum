package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.data.auth.TokenStore
import kotlinx.coroutines.delay

class ChatRepository(
    private val chatApi: ChatApi,
    private val tokenStore: TokenStore,
    private val socketManager: ChatSocketManager,
    private val chatRoomPreferences: ChatRoomPreferences,
) {
    private var realtimeUiListener: ((ChatMessage) -> Unit)? = null
    private var realtimeDeleteListener: ((ChatMessageDeletedEvent) -> Unit)? = null
    private var realtimeTypingListener: ((ChatTypingEvent) -> Unit)? = null

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

    suspend fun sendMessage(
        text: String,
        roomId: String,
        replyToMessageId: String? = null,
    ): Result<ChatMessage> {
        return runCatching {
            chatApi.sendMessage(
                SendMessageRequest(
                    text = text,
                    roomId = roomId,
                    replyToMessageId = replyToMessageId,
                ),
            )
        }
    }

    /** Несколько попыток с задержкой — для UI и оверлея при нестабильной сети. */
    suspend fun sendMessageWithRetries(
        text: String,
        roomId: String,
        replyToMessageId: String? = null,
    ): Result<ChatMessage> {
        var last: Throwable? = null
        repeat(3) { attempt ->
            val r = sendMessage(text, roomId, replyToMessageId)
            if (r.isSuccess) return r
            last = r.exceptionOrNull()
            if (attempt < 2) {
                delay(listOf(400L, 1200L)[attempt])
            }
        }
        return Result.failure(last ?: IllegalStateException("send_failed"))
    }

    suspend fun sendSystemVoiceMessage(text: String): Result<ChatMessage> {
        val roomId = chatRoomPreferences.getSelectedRoomId()
            ?: return Result.failure(IllegalStateException("no_room"))
        return sendMessageWithRetries(text.trim(), roomId)
    }

    suspend fun deleteMessage(messageId: String): Result<ChatMessageDeleteResult> =
        runCatching { chatApi.deleteMessage(messageId) }

    fun connectRealtime(
        roomId: String,
        onMessage: (ChatMessage) -> Unit,
        onDeleteMessage: (ChatMessageDeletedEvent) -> Unit = {},
        onTyping: (ChatTypingEvent) -> Unit = {},
    ) {
        realtimeUiListener?.let { socketManager.removeMessageListener(it) }
        realtimeDeleteListener?.let { socketManager.removeMessageDeletedListener(it) }
        realtimeTypingListener?.let { socketManager.removeTypingListener(it) }
        realtimeUiListener = onMessage
        realtimeDeleteListener = onDeleteMessage
        realtimeTypingListener = onTyping
        socketManager.addMessageListener(onMessage)
        socketManager.addMessageDeletedListener(onDeleteMessage)
        socketManager.addTypingListener(onTyping)
        socketManager.connect(
            baseUrl = BuildConfig.API_BASE_URL,
            roomId = roomId,
            tokenProvider = { tokenStore.getAccessToken() },
        )
    }

    fun emitTypingPing(roomId: String) {
        socketManager.emitTyping(roomId)
    }

    /** Called after OkHttp refreshes access token so the socket re-authenticates. */
    fun onAccessTokenRefreshed() {
        socketManager.reconnectWithFreshToken()
    }

    fun disconnectRealtime() {
        realtimeUiListener?.let { socketManager.removeMessageListener(it) }
        realtimeDeleteListener?.let { socketManager.removeMessageDeletedListener(it) }
        realtimeTypingListener?.let { socketManager.removeTypingListener(it) }
        realtimeUiListener = null
        realtimeDeleteListener = null
        realtimeTypingListener = null
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
