package com.lastasylum.alliance.data.chat

import java.io.File
import kotlinx.coroutines.flow.StateFlow

class ChatRepository(
    chatApi: ChatApi,
    tokenStore: com.lastasylum.alliance.data.auth.TokenStore,
    socketManager: ChatSocketManager,
    private val chatRoomPreferences: ChatRoomPreferences,
) {
    private val rest = ChatRestRepository(chatApi, chatRoomPreferences)
    private val realtime = ChatRealtimeSubscriber(socketManager, tokenStore, chatRoomPreferences)

    suspend fun listRooms(): Result<List<ChatRoomDto>> = rest.listRooms()

    suspend fun getChatSyncState(): Result<ChatSyncStateDto> = rest.getChatSyncState()

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
    ): Result<ChatMessage> = rest.sendMessage(
        text = text,
        roomId = roomId,
        replyToMessageId = replyToMessageId,
        attachments = attachments,
        excavationAlert = excavationAlert,
    )

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

    /** Fire-and-forget socket emit for fast peer delivery (HTTP confirms separately). */
    fun emitChatOutgoingViaSocket(
        text: String,
        roomId: String,
        replyToMessageId: String? = null,
        clientMessageId: String,
        excavationAlert: Boolean = false,
    ) {
        realtime.sendChatMessageViaSocket(
            text = text,
            roomId = roomId,
            replyToMessageId = replyToMessageId,
            clientMessageId = clientMessageId,
            excavationAlert = excavationAlert,
        )
    }

    fun setOutgoingMessageAckListener(listener: (clientMessageId: String, message: ChatMessage) -> Unit) =
        realtime.setOutgoingMessageAckListener(listener)

    /** App/overlay chat UI: parallel socket + HTTP with shared [clientMessageId] for fast peer delivery. */
    suspend fun sendMessageWithRetriesForChatUi(
        text: String,
        roomId: String,
        replyToMessageId: String? = null,
        attachments: List<String>? = null,
        excavationAlert: Boolean = false,
        clientMessageId: String? = null,
        skipSocket: Boolean = false,
    ): Result<ChatMessage> {
        val id = clientMessageId?.trim()?.takeIf { it.isNotEmpty() }
            ?: java.util.UUID.randomUUID().toString()
        if (!skipSocket) {
            realtime.sendChatMessageViaSocket(
                text = text,
                roomId = roomId,
                replyToMessageId = replyToMessageId,
                clientMessageId = id,
                excavationAlert = excavationAlert,
            )
        }
        return rest.sendMessageWithRetries(
            text,
            roomId,
            replyToMessageId,
            attachments,
            excavationAlert,
            onHttpSuccess = null,
            clientMessageId = id,
        )
    }

    /** Overlay quick commands: strip is updated optimistically in [CombatOverlayService] before HTTP. */
    suspend fun sendOverlayRaidCommandWithRetries(
        text: String,
        roomId: String,
        excavationAlert: Boolean = false,
    ): Result<ChatMessage> = rest.sendMessageWithRetries(
        text = text,
        roomId = roomId,
        excavationAlert = excavationAlert,
        onHttpSuccess = null,
    )

    /** Fire-and-forget socket send before outbox persist (overlay hot path). */
    fun prefireOverlayRaidSocket(
        text: String,
        roomId: String,
        clientMessageId: String,
        gameEventAlert: String? = null,
    ) {
        realtime.sendOverlayRaidCommandViaSocket(
            text = text,
            roomId = roomId,
            clientMessageId = clientMessageId,
            gameEventAlert = gameEventAlert,
        )
    }

    /** Overlay quick commands: parallel socket + HTTP with shared [clientMessageId] for idempotent delivery. */
    suspend fun sendOverlayRaidCommandFast(
        text: String,
        roomId: String,
        gameEventAlert: String? = null,
        clientMessageId: String? = null,
        maxAttempts: Int = 3,
    ): Result<ChatMessage> {
        val id = clientMessageId?.trim()?.takeIf { it.isNotEmpty() }
            ?: java.util.UUID.randomUUID().toString()
        realtime.sendOverlayRaidCommandViaSocket(
            text = text,
            roomId = roomId,
            clientMessageId = id,
            gameEventAlert = gameEventAlert,
        )
        return rest.sendOverlayRaidCommandFast(
            text = text,
            roomId = roomId,
            gameEventAlert = gameEventAlert,
            clientMessageId = id,
            maxAttempts = maxAttempts,
        )
    }

    /** REST-only after [prefireOverlayRaidSocket] — avoids duplicate socket emit. */
    suspend fun sendOverlayRaidCommandRestOnly(
        text: String,
        roomId: String,
        gameEventAlert: String? = null,
        clientMessageId: String,
        maxAttempts: Int = 3,
    ): Result<ChatMessage> = rest.sendOverlayRaidCommandFast(
        text = text,
        roomId = roomId,
        gameEventAlert = gameEventAlert,
        clientMessageId = clientMessageId,
        maxAttempts = maxAttempts,
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
        rest.deleteMessage(messageId).also { result ->
            result.onSuccess { deleted ->
                realtime.dispatchOverlayMessageDeleted(
                    ChatMessageDeletedEvent(
                        messageId = deleted.messageId,
                        roomId = deleted.roomId,
                    ),
                )
            }
        }

    suspend fun editMessage(messageId: String, text: String): Result<ChatMessage> =
        rest.editMessage(messageId, text)

    suspend fun toggleReaction(messageId: String, emoji: String): Result<ChatMessage> =
        rest.toggleReaction(messageId, emoji)

    suspend fun forwardMessage(messageId: String, roomId: String): Result<ChatMessage> =
        rest.forwardMessage(messageId, roomId)

    suspend fun markRoomRead(roomId: String, messageId: String): Result<MarkRoomReadResponse> =
        rest.markRoomRead(roomId, messageId)

    suspend fun getPeerReadCursor(roomId: String): Result<String?> =
        rest.getPeerReadCursor(roomId)

    suspend fun clearRoomHistoryForUser(roomId: String): Result<ClearRoomHistoryResponse> =
        rest.clearRoomHistoryForUser(roomId)

    suspend fun pinRoomMessage(roomId: String, messageId: String?): Result<ChatRoomDto> =
        rest.pinRoomMessage(roomId, messageId)

    suspend fun unpinOneRoomMessage(roomId: String, messageId: String): Result<ChatRoomDto> =
        rest.unpinOneRoomMessage(roomId, messageId)

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
        onRoomUnread: (ChatRoomUnreadEvent) -> Unit = {},
        onRoomPinChanged: (ChatRoomPinChangedEvent) -> Unit = {},
        onHistoryCleared: (() -> Unit)? = null,
    ) = realtime.connectRealtimeRooms(
        roomIds,
        onMessage,
        onDeleteMessage,
        onTyping,
        onRead,
        onRoomUnread,
        onRoomPinChanged,
        onHistoryCleared,
    )

    fun emitTypingPing(roomId: String) = realtime.emitTypingPing(roomId)

    fun emitOverlayReaction(
        targetUserId: String,
        reaction: String = "heart",
    ) = realtime.emitOverlayReaction(targetUserId, reaction)

    fun emitOverlayReactionReply(
        targetUserId: String,
        reaction: String,
        replyToLogId: String,
    ) = realtime.emitOverlayReactionReply(targetUserId, reaction, replyToLogId)

    fun emitOverlayReactionBroadcast(reaction: String = "heart") =
        realtime.emitOverlayReactionBroadcast(reaction)

    fun addOverlayReactionListener(listener: (OverlayReactionEvent) -> Unit) =
        realtime.addOverlayReactionListener(listener)

    fun removeOverlayReactionListener(listener: (OverlayReactionEvent) -> Unit) =
        realtime.removeOverlayReactionListener(listener)

    fun addOverlayReactionLogListener(listener: (OverlayReactionLogEntryDto) -> Unit) =
        realtime.addOverlayReactionLogListener(listener)

    fun removeOverlayReactionLogListener(listener: (OverlayReactionLogEntryDto) -> Unit) =
        realtime.removeOverlayReactionLogListener(listener)

    fun addOverlayReactionLogReactionListener(listener: (OverlayReactionLogEntryDto) -> Unit) =
        realtime.addOverlayReactionLogReactionListener(listener)

    fun removeOverlayReactionLogReactionListener(listener: (OverlayReactionLogEntryDto) -> Unit) =
        realtime.removeOverlayReactionLogReactionListener(listener)

    fun isChatSocketConnected(): Boolean = realtime.isChatSocketConnected()

    fun chatConnectionState(): StateFlow<ChatConnectionState> = realtime.chatConnectionState

    fun ensureRoomJoined(roomId: String) = realtime.ensureRoomJoined(roomId)

    fun onAccessTokenRefreshed() = realtime.onAccessTokenRefreshed()

    fun reconnectImmediatelyWithFreshToken() = realtime.reconnectImmediatelyWithFreshToken()

    fun configureReconnectSessionRefresh(refresh: suspend () -> Unit) =
        realtime.configureReconnectSessionRefresh(refresh)

    fun disconnectRealtime() = realtime.disconnectRealtime()

    fun hasPrimaryRealtimeSubscription(): Boolean = realtime.hasPrimaryRealtimeSubscription()

    fun isPrimaryRealtimeRoom(roomId: String): Boolean = realtime.isPrimaryRealtimeRoom(roomId)

    fun addOverlayMessageListener(listener: (ChatMessage) -> Unit) =
        realtime.addOverlayMessageListener(listener)

    fun addOverlayReadListener(listener: (ChatRoomReadEvent) -> Unit) =
        realtime.addOverlayReadListener(listener)

    fun addOverlayTypingListener(listener: (ChatTypingEvent) -> Unit) =
        realtime.addOverlayTypingListener(listener)

    fun removeOverlayTypingListener(listener: (ChatTypingEvent) -> Unit) =
        realtime.removeOverlayTypingListener(listener)

    fun addOverlayRoomUnreadListener(listener: (ChatRoomUnreadEvent) -> Unit) =
        realtime.addOverlayRoomUnreadListener(listener)

    fun removeOverlayRoomUnreadListener(listener: (ChatRoomUnreadEvent) -> Unit) =
        realtime.removeOverlayRoomUnreadListener(listener)

    fun removeOverlayReadListener(listener: (ChatRoomReadEvent) -> Unit) =
        realtime.removeOverlayReadListener(listener)

    fun refreshOverlayRealtimeSubscriptions() =
        realtime.refreshOverlayRealtimeSubscriptions()

    /** Сохранить id комнат «Рейд» и hub «Альянс» без переподписки сокета. */
    fun applyOverlayRoomPreferencesFromRooms(rooms: List<ChatRoomDto>) {
        ChatRaidRoomSync.applyRaidRoomPreference(rooms, chatRoomPreferences)
        ChatHubRoomSync.applyHubRoomPreference(rooms, chatRoomPreferences)
    }

    /** Сохранить id комнат «Рейд» и hub «Альянс», переподписать сокет оверлея. */
    fun applyOverlayRoomsFromRooms(rooms: List<ChatRoomDto>) {
        applyOverlayRoomPreferencesFromRooms(rooms)
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
        realtime.dispatchOverlayHttpMessage(message)
    }

    fun overlayMessageListenerCount(): Int = realtime.overlayMessageListenerCount()

    fun removeOverlayMessageListener(listener: (ChatMessage) -> Unit) =
        realtime.removeOverlayMessageListener(listener)

    fun addOverlayMessageDeletedListener(listener: (ChatMessageDeletedEvent) -> Unit) =
        realtime.addOverlayMessageDeletedListener(listener)

    fun addOverlayChatHistoryClearedListener(listener: () -> Unit) =
        realtime.addOverlayChatHistoryClearedListener(listener)

    fun removeOverlayChatHistoryClearedListener(listener: () -> Unit) =
        realtime.removeOverlayChatHistoryClearedListener(listener)

    fun removeOverlayMessageDeletedListener(listener: (ChatMessageDeletedEvent) -> Unit) =
        realtime.removeOverlayMessageDeletedListener(listener)

    fun notifyOverlayMessageDeleted(messageId: String, roomId: String = "") {
        realtime.dispatchOverlayMessageDeleted(
            ChatMessageDeletedEvent(messageId = messageId, roomId = roomId),
        )
    }

    fun addOverlayChatPanelClosedListener(listener: () -> Unit) =
        realtime.addOverlayChatPanelClosedListener(listener)

    fun removeOverlayChatPanelClosedListener(listener: () -> Unit) =
        realtime.removeOverlayChatPanelClosedListener(listener)

    fun notifyOverlayChatPanelClosed() = realtime.notifyOverlayChatPanelClosed()

    fun resetRealtimeForLogout() = realtime.resetRealtimeForLogout()
}
