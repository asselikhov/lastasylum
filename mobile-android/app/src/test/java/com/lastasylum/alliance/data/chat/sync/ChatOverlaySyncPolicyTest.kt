package com.lastasylum.alliance.data.chat.sync

import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.ui.chat.ChatState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatOverlaySyncPolicyTest {
    private val hubId = "hub-room"
    private val rooms = listOf(
        ChatRoomDto(
            id = hubId,
            allianceId = "pt:team",
            title = "Hub",
            sortOrder = 1,
            unreadCount = 0,
            lastReadMessageId = null,
        ),
    )

    @Test
    fun overlayHubRoomsReady_trueWhenHubSelectedEvenWithoutMessages() {
        val state = ChatState(
            rooms = rooms,
            selectedRoomId = hubId,
            messages = emptyList(),
            isLoading = false,
            isRoomsLoading = false,
        )

        assertTrue(overlayHubRoomsReadyForState(rooms, hubId, state))
    }

    @Test
    fun overlayLoadTimeoutPolicy_emptyRoomWithRoomsReady_clearsLoadingWithoutError() {
        val state = ChatState(
            rooms = rooms,
            selectedRoomId = hubId,
            messages = emptyList(),
            isLoading = true,
        )
        val next = applyOverlayLoadTimeoutPolicy(state, timeoutMessage = "timeout")

        assertFalse(next.isLoading)
        assertTrue(next.error.isNullOrBlank())
    }
}
