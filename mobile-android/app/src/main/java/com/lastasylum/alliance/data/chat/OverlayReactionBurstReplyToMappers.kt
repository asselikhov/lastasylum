package com.lastasylum.alliance.data.chat

fun OverlayReactionLogReplyTo.toBurstReplyTo(): OverlayReactionBurstReplyTo =
    OverlayReactionBurstReplyTo(
        logId = logId,
        reaction = reaction,
        visibility = visibility,
    )
