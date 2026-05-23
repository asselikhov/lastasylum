package com.lastasylum.alliance.data.chat

import java.io.File

class ChatRepository(
    chatApi: ChatApi,
    tokenStore: com.lastasylum.alliance.data.auth.TokenStore,
    socketManager: ChatSocketManager,
    private val chatRoomPreferences: ChatRoomPreferences,
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
        rest.sendExcavationAlertWithRetries(
            text,
            roomId,
            onHttpSuccess = realtime::dispatchOverlayHttpMessage,
        )

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

    suspend fun markRoomRead(roomId: String, messageId: String): Result<MarkRoomReadResponse> =
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

    fun isChatSocketConnected(): Boolean = realtime.isChatSocketConnected()

    fun onAccessTokenRefreshed() = realtime.onAccessTokenRefreshed()

    fun disconnectRealtime() = realtime.disconnectRealtime()

    fun addOverlayMessageListener(listener: (ChatMessage) -> Unit) =
        realtime.addOverlayMessageListener(listener)

    fun addOverlayReadListener(listener: (ChatRoomReadEvent) -> Unit) =
        realtime.addOverlayReadListener(listener)

    fun removeOverlayReadListener(listener: (ChatRoomReadEvent) -> Unit) =
        realtime.removeOverlayReadListener(listener)

    fun refreshOverlayRealtimeSubscriptions() =
        realtime.refreshOverlayRealtimeSubscriptions()

    /** Сохранить id комнат «Рейд» и hub «Альянс», переподписать сокет оверлея. */
    fun applyOverlayRoomsFromRooms(rooms: List<ChatRoomDto>) {
        ChatRaidRoomSync.applyRaidRoomPreference(rooms, chatRoomPreferences)
        ChatHubRoomSync.applyHubRoomPreference(rooms, chatRoomPreferences)
        refreshOverlayRealtimeSubscriptions()
    }

    /** @see applyOverlayRoomsFromRooms */
    fun applyRaidRoomFromRooms(rooms: List<ChatRoomDto>) = applyOverlayRoomsFromRooms(rooms)

    /** Id комнаты «Рейд» для ленты оверлея; при необходимости подгружает список комнат. */
    suspend fun ensureRaidRoomId(): String? {
        chatRoomPreferences.getRaidRoomId()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val rooms = ChatSessionCache.getFreshRooms()
            ?: listRooms().getOrNull()
            ?: return null
        applyOverlayRoomsFromRooms(rooms)
        return chatRoomPreferences.getRaidRoomId()?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Id hub «Альянс» команды — общий голосовой канал для всех участников player team. */
    suspend fun ensureTeamVoiceRoomId(): String? {
        ChatTeamVoiceRoom.roomFromPrefs(chatRoomPreferences)?.let { return it }
        val rooms = ChatSessionCache.getFreshRooms()
            ?: listRooms().getOrNull()
            ?: return null
        return ChatTeamVoiceRoom.syncFromRooms(rooms, chatRoomPreferences)
    }

    fun hubRoomIdFromPrefs(): String? =
        chatRoomPreferences.getHubRoomId()?.trim()?.takeIf { it.isNotEmpty() }

    fun dispatchOverlayHttpMessage(message: ChatMessage) =
        realtime.dispatchOverlayHttpMessage(message)

    /** Raid-room traffic for in-game overlay strip (HTTP echo when socket already handled). */
    fun notifyOverlayRaidStripMessage(message: ChatMessage) {
        if (message.roomId.trim().isEmpty()) return
        realtime.dispatchOverlayHttpMessage(message)
    }

    fun overlayMessageListenerCount(): Int = realtime.overlayMessageListenerCount()

    fun removeOverlayMessageListener(listener: (ChatMessage) -> Unit) =
        realtime.removeOverlayMessageListener(listener)

    fun addOverlayChatPanelClosedListener(listener: () -> Unit) =
        realtime.addOverlayChatPanelClosedListener(listener)

    fun removeOverlayChatPanelClosedListener(listener: () -> Unit) =
        realtime.removeOverlayChatPanelClosedListener(listener)

    fun notifyOverlayChatPanelClosed() = realtime.notifyOverlayChatPanelClosed()

    fun resetRealtimeForLogout() = realtime.resetRealtimeForLogout()
}
