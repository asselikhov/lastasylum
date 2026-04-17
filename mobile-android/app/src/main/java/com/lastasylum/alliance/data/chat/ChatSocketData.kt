package com.lastasylum.alliance.data.chat

data class OutgoingSocketMessage(
    val text: String,
    val allianceId: String = "OBZHORY",
)
