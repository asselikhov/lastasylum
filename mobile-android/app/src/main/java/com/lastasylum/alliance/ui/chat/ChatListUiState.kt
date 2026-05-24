package com.lastasylum.alliance.ui.chat

import androidx.compose.runtime.Immutable

/** Narrow snapshot for the message list — avoids recomposing bubbles on unrelated [ChatState] changes. */
@Immutable
data class ChatListUiState(
    val isRoomsLoading: Boolean,
    val roomsEmpty: Boolean,
    val isLoading: Boolean,
    val isLoadingOlder: Boolean,
    val error: String?,
    val currentUserId: String,
    val isAppAdmin: Boolean,
    val playerTeamSquadRole: String?,
    val deletingMessageId: String?,
)

fun ChatState.toChatListUiState(): ChatListUiState =
    ChatListUiState(
        isRoomsLoading = isRoomsLoading,
        roomsEmpty = rooms.isEmpty(),
        isLoading = isLoading,
        isLoadingOlder = isLoadingOlder,
        error = error,
        currentUserId = currentUserId,
        isAppAdmin = isAppAdmin,
        playerTeamSquadRole = playerTeamSquadRole,
        deletingMessageId = deletingMessageId,
    )
