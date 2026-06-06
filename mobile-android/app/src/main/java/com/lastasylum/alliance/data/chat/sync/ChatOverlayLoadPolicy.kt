package com.lastasylum.alliance.data.chat.sync

import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.ui.chat.ChatState

internal fun overlayHubRoomsReadyForState(
    rooms: List<ChatRoomDto>,
    hubId: String?,
    state: ChatState,
): Boolean {
    if (rooms.isEmpty()) return false
    val resolvedHubId = hubId ?: return false
    return !state.isRoomsLoading &&
        state.error.isNullOrBlank() &&
        (state.selectedRoomId == resolvedHubId || state.selectedRoomId.isNullOrBlank())
}

/** Empty overlay room with rooms list loaded: clear loading without surfacing a timeout error. */
internal fun applyOverlayLoadTimeoutPolicy(state: ChatState, timeoutMessage: String): ChatState {
    if (!state.isLoading && !state.isRoomsLoading) return state
    val roomsReady = state.rooms.isNotEmpty()
    val messagesEmpty = state.messages.isEmpty()
    return if (roomsReady && messagesEmpty) {
        state.copy(isLoading = false, isRoomsLoading = false, error = null)
    } else {
        state.copy(
            isLoading = false,
            isRoomsLoading = false,
            error = state.error ?: timeoutMessage,
        )
    }
}
