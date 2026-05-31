package com.lastasylum.alliance.overlay

/** When overlay quick-command HTTP responses should merge into the visible chat list. */
internal object OverlayRaidChatForwardPolicy {
    fun shouldApplyToVisibleChat(selectedRoomId: String?, messageRoomId: String): Boolean {
        val rid = messageRoomId.trim()
        val selected = selectedRoomId?.trim().orEmpty()
        return rid.isNotEmpty() && rid == selected
    }
}
