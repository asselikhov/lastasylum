package com.lastasylum.alliance.ui.chat

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lastasylum.alliance.data.ReadCursorSession
import com.lastasylum.alliance.data.cache.LaunchDiskCache
import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import com.lastasylum.alliance.data.chat.PinHistoryPreferences
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.data.teams.TeamForumPreferences
import com.lastasylum.alliance.data.users.UsersRepository

class ChatViewModelFactory(
    private val application: Application,
    private val repository: ChatRepository,
    private val chatRoomPreferences: ChatRoomPreferences,
    private val pinHistoryPreferences: PinHistoryPreferences,
    private val teamForumPreferences: TeamForumPreferences,
    private val userSettingsPreferences: UserSettingsPreferences,
    private val usersRepository: UsersRepository,
    private val launchDiskCache: LaunchDiskCache,
    private val currentUserId: String,
    private val currentUserRole: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            ReadCursorSession.bind(
                chatRoomPreferences,
                teamForumPreferences,
                userSettingsPreferences,
                currentUserId,
            )
            pinHistoryPreferences.bindUser(currentUserId)
            return ChatViewModel(
                application,
                repository,
                chatRoomPreferences,
                pinHistoryPreferences,
                usersRepository,
                launchDiskCache,
                currentUserId,
                currentUserRole,
            ) as T
        }
        error("Unsupported ViewModel class: ${modelClass.name}")
    }
}
