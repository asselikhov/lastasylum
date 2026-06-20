package com.lastasylum.alliance.di

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.ui.chat.ChatViewModel

/** Process-wide handle to the activity-scoped chat VM (set at login from [SquadRelayApp]). */
object ChatViewModelRegistry {
    @Volatile
    var shared: ChatViewModel? = null
        private set

    fun bind(viewModel: ChatViewModel?) {
        shared = viewModel
    }

    /** Background outbox worker resume — refresh VM list when activity-scoped VM is bound. */
    fun onOutboxSendSuccess(
        clientMessageId: String,
        sent: ChatMessage,
        pendingIdHint: String?,
    ) {
        shared?.onBackgroundOutboxConfirmed(clientMessageId, sent, pendingIdHint)
    }
}
