package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.data.auth.TokenStore

class ChatRepository(
    private val chatApi: ChatApi,
    private val tokenStore: TokenStore,
    private val socketManager: ChatSocketManager,
    private val chatRoomPreferences: ChatRoomPreferences,
) {
    suspend fun listRooms(): Result<List<ChatRoomDto>> =
        runCatching { chatApi.listRooms() }

    suspend fun loadRecentMessages(roomId: String): Result<List<ChatMessage>> {
        return runCatching { chatApi.getMessages(roomId = roomId) }
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
        val accessToken = tokenStore.getAccessToken() ?: return
        socketManager.connect(
            baseUrl = BuildConfig.API_BASE_URL,
            accessToken = accessToken,
            roomId = roomId,
            onNewMessage = onMessage,
        )
    }

    fun disconnectRealtime() {
        socketManager.disconnect()
    }
}
