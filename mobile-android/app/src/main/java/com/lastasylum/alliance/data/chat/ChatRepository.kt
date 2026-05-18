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
    /** Primary chat tab + alliance «Рейд» so overlay can receive raid while another tab is open. */
    private fun realtimeRoomIdsForPrimary(primaryRoomId: String): List<String> {
        val raid = chatRoomPreferences.getRaidRoomId()
        return buildList {
            add(primaryRoomId)
            if (!raid.isNullOrBlank() && raid != primaryRoomId) add(raid)
        }
    }

    /** When only the overlay is active: subscribe to raid first, then any stored selected room. */
    private fun realtimeRoomIdsForOverlayBootstrap(): List<String> {
        val raid = chatRoomPreferences.getRaidRoomId()
        val selected = chatRoomPreferences.getSelectedRoomId()
        return buildList {
            if (!raid.isNullOrBlank()) add(raid)
            if (!selected.isNullOrBlank() && selected !in this) add(selected)
        }
    }

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

    suspend fun uploadImageFile(roomId: String, file: File, mimeType: String): Result<UploadChatAttachmentResponse> =
        uploadAttachmentFile(roomId, file, mimeType, file.name)

    suspend fun uploadAttachmentFile(
        roomId: String,
        file: File,
        mimeType: String,
        uploadFilename: String,
    ): Result<UploadChatAttachmentResponse> = runCatching {
        val body = file.asRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", uploadFilename, body)
        val roomPart = roomId.toRequestBody("text/plain".toMediaTypeOrNull())
        chatApi.uploadAttachment(part, roomPart)
    }

    suspend fun sendSystemVoiceMessage(text: String): Result<ChatMessage> {
        val roomId = chatRoomPreferences.getRaidRoomId()
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
            roomIds = realtimeRoomIdsForPrimary(roomId),
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
        val roomIds = realtimeRoomIdsForOverlayBootstrap()
        if (roomIds.isEmpty()) return
        socketManager.connect(
            baseUrl = BuildConfig.API_BASE_URL,
            roomIds = roomIds,
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
