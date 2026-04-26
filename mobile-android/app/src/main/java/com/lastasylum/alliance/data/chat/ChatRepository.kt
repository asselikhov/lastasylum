package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.data.auth.TokenStore
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class ChatRepository(
    private val chatApi: ChatApi,
    private val tokenStore: TokenStore,
    private val socketManager: ChatSocketManager,
    private val chatRoomPreferences: ChatRoomPreferences,
) {
    private var realtimeUiListener: ((ChatMessage) -> Unit)? = null
    private var realtimeDeleteListener: ((ChatMessageDeletedEvent) -> Unit)? = null
    private var realtimeTypingListener: ((ChatTypingEvent) -> Unit)? = null
    private var realtimeReadListener: ((ChatRoomReadEvent) -> Unit)? = null
    private val overlayMessageListeners = java.util.concurrent.CopyOnWriteArrayList<(ChatMessage) -> Unit>()

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
        attachments: List<String>? = null,
    ): Result<ChatMessage> {
        return runCatching {
            chatApi.sendMessage(
                SendMessageRequest(
                    text = text,
                    roomId = roomId,
                    replyToMessageId = replyToMessageId,
                    attachments = attachments,
                ),
            )
        }
    }

    /** Несколько попыток с задержкой — для UI и оверлея при нестабильной сети. */
    suspend fun sendMessageWithRetries(
        text: String,
        roomId: String,
        replyToMessageId: String? = null,
        attachments: List<String>? = null,
    ): Result<ChatMessage> {
        var last: Throwable? = null
        repeat(3) { attempt ->
            val r = sendMessage(text, roomId, replyToMessageId, attachments)
            if (r.isSuccess) {
                // For the overlay strip we want the message to appear immediately after HTTP success,
                // even if the socket broadcast is delayed or the room subscription is rebuilding.
                r.getOrNull()?.let { sent ->
                    overlayMessageListeners.forEach { l -> runCatching { l(sent) } }
                }
                return r
            }
            last = r.exceptionOrNull()
            if (attempt < 2) {
                delay(listOf(400L, 1200L)[attempt])
            }
        }
        return Result.failure(last ?: IllegalStateException("send_failed"))
    }

    suspend fun uploadImageFile(roomId: String, file: File, mimeType: String): Result<UploadChatAttachmentResponse> {
        return runCatching {
            val body = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, body)
            val roomPart = roomId.toRequestBody("text/plain".toMediaTypeOrNull())
            chatApi.uploadAttachment(part, roomPart)
        }
    }

    suspend fun sendSystemVoiceMessage(text: String): Result<ChatMessage> {
        val roomId = chatRoomPreferences.getSelectedRoomId()
            ?: return Result.failure(IllegalStateException("no_room"))
        return sendMessageWithRetries(text.trim(), roomId)
    }

    suspend fun deleteMessage(messageId: String): Result<ChatMessageDeleteResult> =
        runCatching { chatApi.deleteMessage(messageId) }

    suspend fun editMessage(messageId: String, text: String): Result<ChatMessage> =
        runCatching { chatApi.editMessage(messageId, EditMessageRequest(text = text)) }

    suspend fun toggleReaction(messageId: String, emoji: String): Result<ChatMessage> =
        runCatching { chatApi.toggleReaction(messageId, ToggleReactionRequest(emoji = emoji)) }

    suspend fun forwardMessage(messageId: String, roomId: String): Result<ChatMessage> =
        runCatching { chatApi.forwardMessage(messageId, ForwardMessageRequest(roomId = roomId)) }

    suspend fun markRoomRead(roomId: String, messageId: String): Result<Unit> =
        runCatching {
            chatApi.markRoomRead(roomId, MarkRoomReadRequest(messageId = messageId))
            Unit
        }

    fun connectRealtime(
        roomId: String,
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
        realtimeReadListener?.let { socketManager.removeReadListener(it) }
        realtimeUiListener = null
        realtimeDeleteListener = null
        realtimeTypingListener = null
        realtimeReadListener = null
        socketManager.disconnect()
    }

    fun addOverlayMessageListener(listener: (ChatMessage) -> Unit) {
        if (!overlayMessageListeners.contains(listener)) {
            overlayMessageListeners.add(listener)
        }
        socketManager.addMessageListener(listener)
        val roomId = chatRoomPreferences.getSelectedRoomId() ?: return
        socketManager.connect(
            baseUrl = BuildConfig.API_BASE_URL,
            roomId = roomId,
            tokenProvider = { tokenStore.getAccessToken() },
        )
    }

    fun removeOverlayMessageListener(listener: (ChatMessage) -> Unit) {
        overlayMessageListeners.remove(listener)
        socketManager.removeMessageListener(listener)
    }

    fun resetRealtimeForLogout() {
        realtimeUiListener?.let { socketManager.removeMessageListener(it) }
        realtimeUiListener = null
        socketManager.disconnectSocketAndClearListeners()
    }
}
