package com.lastasylum.alliance.data.chat

import java.io.File

class ChatRepository(
    chatApi: ChatApi,
    tokenStore: com.lastasylum.alliance.data.auth.TokenStore,
    socketManager: ChatSocketManager,
    chatRoomPreferences: ChatRoomPreferences,
) {
    private val rest = ChatRestRepository(chatApi, chatRoomPreferences)
    private val realtime = ChatRealtimeSubscriber(socketManager, tokenStore, chatRoomPreferences)

    suspend fun listRooms(): Result<List<ChatRoomDto>> = rest.listRooms()

    suspend fun loadRecentMessages(
        roomId: String,
        beforeMessageId: String? = null,
        limit: Int = 30,
    ): Result<List<ChatMessage>> = rest.loadRecentMessages(roomId, beforeMessageId, limit)

    suspend fun sendMessage(
        text: String,
        roomId: String,
        replyToMessageId: String? = null,
        attachments: List<String>? = null,
        excavationAlert: Boolean = false,
    ): Result<ChatMessage> = rest.sendMessage(text, roomId, replyToMessageId, attachments, excavationAlert)

    suspend fun sendExcavationAlertWithRetries(text: String, roomId: String): Result<ChatMessage> =
        rest.sendExcavationAlertWithRetries(text, roomId)

    suspend fun sendMessageWithRetries(
        text: String,
        roomId: String,
        replyToMessageId: String? = null,
        attachments: List<String>? = null,
        excavationAlert: Boolean = false,
    ): Result<ChatMessage> =
        rest.sendMessageWithRetries(
            text,
            roomId,
            replyToMessageId,
            attachments,
            excavationAlert,
            onHttpSuccess = realtime::dispatchOverlayHttpMessage,
        )

    suspend fun uploadImageFile(roomId: String, file: File, mimeType: String): Result<UploadChatAttachmentResponse> =
        rest.uploadImageFile(roomId, file, mimeType)

    suspend fun uploadAttachmentFile(
        roomId: String,
        file: File,
        mimeType: String,
        uploadFilename: String,
    ): Result<UploadChatAttachmentResponse> =
        rest.uploadAttachmentFile(roomId, file, mimeType, uploadFilename)

    suspend fun sendSystemVoiceMessage(text: String): Result<ChatMessage> =
        rest.sendSystemVoiceMessage(text)

    suspend fun deleteMessage(messageId: String): Result<ChatMessageDeleteResult> =
        rest.deleteMessage(messageId)

    suspend fun editMessage(messageId: String, text: String): Result<ChatMessage> =
        rest.editMessage(messageId, text)

    suspend fun toggleReaction(messageId: String, emoji: String): Result<ChatMessage> =
        rest.toggleReaction(messageId, emoji)

    suspend fun forwardMessage(messageId: String, roomId: String): Result<ChatMessage> =
        rest.forwardMessage(messageId, roomId)

    suspend fun markRoomRead(roomId: String, messageId: String): Result<Unit> =
        rest.markRoomRead(roomId, messageId)

    fun connectRealtime(
        roomId: String,
        onMessage: (ChatMessage) -> Unit,
        onDeleteMessage: (ChatMessageDeletedEvent) -> Unit = {},
        onTyping: (ChatTypingEvent) -> Unit = {},
        onRead: (ChatRoomReadEvent) -> Unit = {},
    ) = realtime.connectRealtime(roomId, onMessage, onDeleteMessage, onTyping, onRead)

    fun connectRealtimeRooms(
        roomIds: List<String>,
        onMessage: (ChatMessage) -> Unit,
        onDeleteMessage: (ChatMessageDeletedEvent) -> Unit = {},
        onTyping: (ChatTypingEvent) -> Unit = {},
        onRead: (ChatRoomReadEvent) -> Unit = {},
    ) = realtime.connectRealtimeRooms(roomIds, onMessage, onDeleteMessage, onTyping, onRead)

    fun emitTypingPing(roomId: String) = realtime.emitTypingPing(roomId)

    fun emitOverlayReaction(targetUserId: String, reaction: String = "heart") =
        realtime.emitOverlayReaction(targetUserId, reaction)

    fun emitOverlayReactionBroadcast(reaction: String = "heart") =
        realtime.emitOverlayReactionBroadcast(reaction)

    fun addOverlayReactionListener(listener: (OverlayReactionEvent) -> Unit) =
        realtime.addOverlayReactionListener(listener)

    fun removeOverlayReactionListener(listener: (OverlayReactionEvent) -> Unit) =
        realtime.removeOverlayReactionListener(listener)

    fun onAccessTokenRefreshed() = realtime.onAccessTokenRefreshed()

    fun disconnectRealtime() = realtime.disconnectRealtime()

    fun addOverlayMessageListener(listener: (ChatMessage) -> Unit) =
        realtime.addOverlayMessageListener(listener)

    fun removeOverlayMessageListener(listener: (ChatMessage) -> Unit) =
        realtime.removeOverlayMessageListener(listener)

    fun addOverlayChatPanelClosedListener(listener: () -> Unit) =
        realtime.addOverlayChatPanelClosedListener(listener)

    fun removeOverlayChatPanelClosedListener(listener: () -> Unit) =
        realtime.removeOverlayChatPanelClosedListener(listener)

    fun notifyOverlayChatPanelClosed() = realtime.notifyOverlayChatPanelClosed()

    fun resetRealtimeForLogout() = realtime.resetRealtimeForLogout()
}
