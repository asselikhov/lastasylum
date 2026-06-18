package com.lastasylum.alliance.overlay

/** When overlay hub/alliance socket rows should merge into the visible chat list. */
internal object OverlayHubChatForwardPolicy {
    fun shouldApplyToVisibleChat(
        overlayPanelVisible: Boolean,
        overlayChatContentActive: Boolean,
        selectedRoomId: String?,
        messageRoomId: String,
        hubRoomId: String,
    ): Boolean {
        if (!overlayPanelVisible || !overlayChatContentActive) return false
        val rid = messageRoomId.trim()
        if (rid.isEmpty()) return false
        val selected = selectedRoomId?.trim().orEmpty()
        val hub = hubRoomId.trim()
        return rid == selected || (hub.isNotEmpty() && rid == hub && selected == hub)
    }
}
