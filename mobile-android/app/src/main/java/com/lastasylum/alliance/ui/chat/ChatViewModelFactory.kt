package com.lastasylum.alliance.ui.chat

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.data.chat.ChatRoomPreferences

class ChatViewModelFactory(
    private val application: Application,
    private val repository: ChatRepository,
    private val chatRoomPreferences: ChatRoomPreferences,
    private val currentUserId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                application,
                repository,
                chatRoomPreferences,
                currentUserId,
            ) as T
        }
        error("Unsupported ViewModel class: ${modelClass.name}")
    }
}
