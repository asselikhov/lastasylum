package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage

/**
 * Raid strip routing helpers (full window lifecycle stays in [CombatOverlayService]).
 */
internal object OverlayRaidStripController {
    fun shouldShowOnStrip(msg: ChatMessage, raidRoomId: String?): Boolean =
        OverlayStripMessageRouter.isOverlayRaidRoomMessage(msg, raidRoomId)

    fun shouldBumpHubUnread(msg: ChatMessage, hubRoomId: String?, raidRoomId: String?): Boolean =
        OverlayStripMessageRouter.shouldRouteHubUnread(msg, hubRoomId, raidRoomId)

    fun noticeSignature(noticeId: String, message: String): String = "notice:$noticeId:$message"
}
