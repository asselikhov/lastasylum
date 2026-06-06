package com.lastasylum.alliance.overlay

/** When overlay quick-command HTTP responses should merge into the visible chat list. */
internal object OverlayRaidChatForwardPolicy {
    fun shouldApplyToVisibleChat(
        selectedRoomId: String?,
        messageRoomId: String,
        overlayPanelVisible: Boolean = false,
        isOwnQuickCommandResponse: Boolean = false,
    ): Boolean {
        val rid = messageRoomId.trim()
        if (rid.isEmpty()) return false
        val selected = selectedRoomId?.trim().orEmpty()
        if (rid == selected) return true
        return overlayPanelVisible && isOwnQuickCommandResponse
    }
}
