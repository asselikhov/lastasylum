package com.lastasylum.alliance.data.chat.sync

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.MarkRoomReadResponse

/** Narrow REST surface used by [ChatSyncEngine] — testable without full [ChatRepository]. */
interface ChatSyncRestGateway {
    suspend fun listRooms(): Result<List<ChatRoomDto>>
    suspend fun markRoomRead(roomId: String, messageId: String): Result<MarkRoomReadResponse>
    suspend fun sendMessageWithRetriesForChatUi(
        text: String,
        roomId: String,
        replyToMessageId: String?,
        attachments: List<String>?,
        excavationAlert: Boolean,
        clientMessageId: String?,
    ): Result<ChatMessage>

    suspend fun sendOverlayRaidCommandFast(
        text: String,
        roomId: String,
        gameEventAlert: String?,
        clientMessageId: String,
        maxAttempts: Int = 3,
    ): Result<ChatMessage>
}

fun ChatRepository.asSyncGateway(): ChatSyncRestGateway = object : ChatSyncRestGateway {
    override suspend fun listRooms(): Result<List<ChatRoomDto>> = this@asSyncGateway.listRooms()
    override suspend fun markRoomRead(roomId: String, messageId: String): Result<MarkRoomReadResponse> =
        this@asSyncGateway.markRoomRead(roomId, messageId)
    override suspend fun sendMessageWithRetriesForChatUi(
        text: String,
        roomId: String,
        replyToMessageId: String?,
        attachments: List<String>?,
        excavationAlert: Boolean,
        clientMessageId: String?,
    ): Result<ChatMessage> = this@asSyncGateway.sendMessageWithRetriesForChatUi(
        text, roomId, replyToMessageId, attachments, excavationAlert, clientMessageId,
    )

    override suspend fun sendOverlayRaidCommandFast(
        text: String,
        roomId: String,
        gameEventAlert: String?,
        clientMessageId: String,
        maxAttempts: Int,
    ): Result<ChatMessage> = this@asSyncGateway.sendOverlayRaidCommandFast(
        text = text,
        roomId = roomId,
        gameEventAlert = gameEventAlert,
        clientMessageId = clientMessageId,
        maxAttempts = maxAttempts,
    )
}
