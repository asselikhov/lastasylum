package com.lastasylum.alliance.data.chat

data class OverlayReactionEvent(
    val fromUserId: String,
    val fromUsername: String,
    val reaction: String,
    val targetUserId: String,
    /** True when sent via overlay:reaction:broadcast (all ingame teammates). */
    val broadcast: Boolean = false,
    val logEntryId: String? = null,
    val replyToLogId: String? = null,
    val replyToLog: OverlayReactionBurstReplyTo? = null,
)
