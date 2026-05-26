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
    toChatListUiState(toListPane(), toChromePane())

fun toChatListUiState(
    listPane: ChatListPaneState,
    chromePane: ChatChromePaneState,
): ChatListUiState =
    ChatListUiState(
        isRoomsLoading = chromePane.isRoomsLoading,
        roomsEmpty = chromePane.rooms.isEmpty(),
        isLoading = listPane.isLoading,
        isLoadingOlder = listPane.isLoadingOlder,
        error = chromePane.error,
        currentUserId = chromePane.currentUserId,
        isAppAdmin = chromePane.isAppAdmin,
        playerTeamSquadRole = chromePane.playerTeamSquadRole,
        deletingMessageId = listPane.deletingMessageId,
    )
