package com.lastasylum.alliance.data.chat

data class OverlayReactionEvent(
    val fromUserId: String,
    val fromUsername: String,
    val reaction: String,
    val targetUserId: String,
)
