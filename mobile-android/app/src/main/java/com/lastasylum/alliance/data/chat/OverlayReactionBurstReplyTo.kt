package com.lastasylum.alliance.data.chat

/** Parent reaction snapshot for an overlay reply (burst + socket). */
data class OverlayReactionBurstReplyTo(
    val logId: String,
    val reaction: String,
    val visibility: OverlayReactionLogVisibility,
)
