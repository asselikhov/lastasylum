package com.lastasylum.alliance.data.chat

import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/** HTTP-only chat API (no Socket.IO). */
class ChatRestRepository(
    private val chatApi: ChatApi,
    private val chatRoomPreferences: ChatRoomPreferences,
) {
    suspend fun listRooms(): Result<List<ChatRoomDto>> =
        ChatRoomsSessionCache.listRooms { chatApi.listRooms() }

    suspend fun loadRecentMessages(
        roomId: String,
        beforeMessageId: String? = null,
        limit: Int = 30,
    ): Result<List<ChatMessage>> =
        runCatching {
            chatApi.getMessages(roomId = roomId, before = beforeMessageId, limit = limit)
        }

    suspend fun sendMessage(
        text: String,
        roomId: String,
        replyToMessageId: String? = null,
        attachments: List<String>? = null,
        excavationAlert: Boolean = false,
    ): Result<ChatMessage> =
        runCatching {
            chatApi.sendMessage(
                SendMessageRequest(
                    text = text,
                    roomId = roomId,
                    replyToMessageId = replyToMessageId,
                    attachments = attachments,
                    excavationAlert = if (excavationAlert) true else null,
                ),
            )
        }

    suspend fun sendExcavationAlertWithRetries(
        text: String,
        roomId: String,
        onHttpSuccess: ((ChatMessage) -> Unit)? = null,
    ): Result<ChatMessage> =
        sendMessageWithRetries(text, roomId, excavationAlert = true, onHttpSuccess = onHttpSuccess)

    suspend fun sendMessageWithRetries(
        text: String,
        roomId: String,
        replyToMessageId: String? = null,
        attachments: List<String>? = null,
        excavationAlert: Boolean = false,
        onHttpSuccess: ((ChatMessage) -> Unit)? = null,
    ): Result<ChatMessage> {
        var last: Throwable? = null
        repeat(3) { attempt ->
            val r = sendMessage(text, roomId, replyToMessageId, attachments, excavationAlert)
            if (r.isSuccess) {
                r.getOrNull()?.let { sent -> onHttpSuccess?.invoke(sent) }
                return r
            }
            last = r.exceptionOrNull()
            if (attempt < 2) {
                delay(listOf(120L, 350L)[attempt])
            }
        }
        return Result.failure(last ?: IllegalStateException("send_failed"))
    }

    /** Overlay quick commands: fewer retries, shorter backoff than [sendMessageWithRetries]. */
    suspend fun sendOverlayRaidCommandFast(
        text: String,
        roomId: String,
        excavationAlert: Boolean = false,
    ): Result<ChatMessage> {
        var last: Throwable? = null
        repeat(2) { attempt ->
            val r = sendMessage(text, roomId, excavationAlert = excavationAlert)
            if (r.isSuccess) return r
            last = r.exceptionOrNull()
            if (attempt < 1) delay(80L)
        }
        return Result.failure(last ?: IllegalStateException("send_failed"))
    }

    suspend fun uploadImageFile(
        roomId: String,
        file: File,
        mimeType: String,
    ): Result<UploadChatAttachmentResponse> =
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

    suspend fun markRoomRead(roomId: String, messageId: String): Result<MarkRoomReadResponse> =
        runCatching {
            chatApi.markRoomRead(roomId, MarkRoomReadRequest(messageId = messageId))
        }

    suspend fun clearRoomHistoryForUser(roomId: String): Result<ClearRoomHistoryResponse> =
        runCatching { chatApi.clearRoomHistory(roomId) }
}
