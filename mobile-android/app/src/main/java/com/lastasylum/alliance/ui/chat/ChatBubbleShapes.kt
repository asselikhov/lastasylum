package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val ChatBubbleChainRadius = 18.dp
private val ChatBubbleTailCorner = 3.dp

/** Размер аватара собеседника в списке чата (как во вкладке «Чат»). */
val ChatIncomingAvatarSize = 38.dp

val ChatIncomingAvatarEndPad = 6.dp

fun chatBubbleShapeOutgoing(isChainBottom: Boolean): RoundedCornerShape =
    if (isChainBottom) {
        RoundedCornerShape(
            topStart = ChatBubbleChainRadius,
            topEnd = ChatBubbleChainRadius,
            bottomStart = ChatBubbleChainRadius,
            bottomEnd = ChatBubbleTailCorner,
        )
    } else {
        RoundedCornerShape(ChatBubbleChainRadius)
    }

fun chatBubbleShapeIncoming(isChainBottom: Boolean): RoundedCornerShape =
    if (isChainBottom) {
        RoundedCornerShape(
            topStart = ChatBubbleChainRadius,
            topEnd = ChatBubbleChainRadius,
            bottomStart = ChatBubbleTailCorner,
            bottomEnd = ChatBubbleChainRadius,
        )
    } else {
        RoundedCornerShape(ChatBubbleChainRadius)
    }
