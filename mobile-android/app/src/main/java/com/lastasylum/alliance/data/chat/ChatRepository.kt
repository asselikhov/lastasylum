package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.data.auth.TokenStore

class ChatRepository(
    private val chatApi: ChatApi,
    private val tokenStore: TokenStore,
    private val socketManager: ChatSocketManager,
) {
    suspend fun loadRecentMessages(): Result<List<ChatMessage>> {
        return runCatching { chatApi.getMessages() }
    }

    suspend fun sendMessage(text: String): Result<ChatMessage> {
        return runCatching { chatApi.sendMessage(SendMessageRequest(text = text)) }
    }

    suspend fun sendSystemVoiceMessage(text: String): Result<ChatMessage> {
        return runCatching { chatApi.sendMessage(SendMessageRequest(text = text.trim())) }
    }

    fun connectRealtime(onMessage: (ChatMessage) -> Unit) {
        val accessToken = tokenStore.getAccessToken() ?: return
        socketManager.connect(
            baseUrl = BuildConfig.API_BASE_URL,
            accessToken = accessToken,
            onNewMessage = onMessage,
        )
    }

    fun sendRealtimeMessage(text: String) {
        socketManager.sendMessage(text = text)
    }

    fun disconnectRealtime() {
        socketManager.disconnect()
    }
}
