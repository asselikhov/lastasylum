package com.lastasylum.alliance.di

import com.lastasylum.alliance.ui.chat.ChatViewModel

/** Process-wide handle to the activity-scoped chat VM (set at login from [SquadRelayApp]). */
object ChatViewModelRegistry {
    @Volatile
    var shared: ChatViewModel? = null
        private set

    fun bind(viewModel: ChatViewModel?) {
        shared = viewModel
    }
}
