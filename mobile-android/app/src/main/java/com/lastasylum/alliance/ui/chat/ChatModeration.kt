package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.auth.AccountRoles
import com.lastasylum.alliance.data.chat.ChatAllianceIds
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto

/** App admin account ([AccountRoles.ADMIN]), not squad rank R5. */
fun isAppAdmin(allianceAccountRole: String): Boolean =
    AccountRoles.isAppAdmin(allianceAccountRole)

/**
 * May delete/edit another user's message in chat.
 * — «Межсерв» / `#server`: app admin only;
 * — `pt:*` team rooms: squad R4 or R5.
 */
fun canModerateChatMessage(
    message: ChatMessage,
    currentUserId: String,
    isAppAdmin: Boolean,
    playerTeamSquadRole: String?,
): Boolean {
    if (message._id.isNullOrBlank()) return false
    if (message.senderId == currentUserId) return true
    val allianceId = message.allianceId
    if (
        allianceId == ChatAllianceIds.GLOBAL ||
        ChatAllianceIds.isServerScope(allianceId)
    ) {
        return isAppAdmin
    }
    if (allianceId.startsWith("pt:")) {
        val r = playerTeamSquadRole?.trim()?.uppercase() ?: return false
        return r == "R4" || r == "R5"
    }
    return false
}

/** Prefer room scope; fall back to message scope when room DTO omits allianceId. */
fun resolveChatPinAllianceId(
    room: ChatRoomDto?,
    message: ChatMessage? = null,
): String? {
    val fromRoom = room?.allianceId?.trim().orEmpty()
    if (fromRoom.isNotEmpty()) return fromRoom
    val fromMessage = message?.allianceId?.trim().orEmpty()
    return fromMessage.takeIf { it.isNotEmpty() }
}

/** Squad R4/R5 may pin/unpin in `pt:*` team chat rooms. */
fun canPinChatMessage(
    allianceId: String?,
    playerTeamSquadRole: String?,
): Boolean {
    val scope = allianceId?.trim().orEmpty()
    if (!scope.startsWith("pt:")) return false
    val r = playerTeamSquadRole?.trim()?.uppercase() ?: return false
    return r == "R4" || r == "R5"
}

/** @deprecated Use [canModerateChatMessage]. */
fun canDeleteChatMessage(
    message: ChatMessage,
    currentUserId: String,
    isAppAdmin: Boolean,
    playerTeamSquadRole: String?,
): Boolean = canModerateChatMessage(message, currentUserId, isAppAdmin, playerTeamSquadRole)
