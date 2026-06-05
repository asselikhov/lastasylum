package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.OverlayReactionEvent

/**
 * Reaction routing helpers extracted from [CombatOverlayService].
 */
internal object OverlayReactionCoordinator {
    fun shouldDeliverIncomingReaction(
        event: OverlayReactionEvent,
        selfUserId: String,
        overlaySessionActive: Boolean,
    ): Boolean {
        if (!overlaySessionActive) return false
        if (selfUserId.isBlank()) return false
        if (event.fromUserId == selfUserId) return false
        return event.targetUserId == selfUserId
    }
}
