package com.lastasylum.alliance.ui.chat

import androidx.compose.ui.unit.Dp

/** Группировка пузырей (аватар, заголовок, скругления цепочки). */
data class ChatMessageClusterFlags(
    val showHeader: Boolean,
    val isChainBottom: Boolean,
    val tightInnerTop: Boolean,
)

/** Форум: те же флаги + отступ сверху (лента oldest-first). */
data class ForumMessageClusterFlags(
    val showHeader: Boolean,
    val isChainBottom: Boolean,
    val tightInnerTop: Boolean,
    val topSpacing: Dp,
)

fun ForumMessageClusterFlags.toChatClusterFlags(): ChatMessageClusterFlags =
    ChatMessageClusterFlags(
        showHeader = showHeader,
        isChainBottom = isChainBottom,
        tightInnerTop = tightInnerTop,
    )
