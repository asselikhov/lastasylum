package com.lastasylum.alliance.ui.chat

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import com.lastasylum.alliance.data.users.UsersRepository

class ChatViewModelFactory(
    private val application: Application,
    private val repository: ChatRepository,
    private val chatRoomPreferences: ChatRoomPreferences,
    private val usersRepository: UsersRepository,
    private val currentUserId: String,
    private val currentUserRole: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                application,
                repository,
                chatRoomPreferences,
                usersRepository,
                currentUserId,
                currentUserRole,
            ) as T
        }
        error("Unsupported ViewModel class: ${modelClass.name}")
    }
}
