package com.lastasylum.alliance.ui.chat

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp

/**
 * When set (main/forum message [androidx.compose.foundation.lazy.LazyColumn]),
 * [ChatMessageBubbleRow] skips per-item [androidx.compose.foundation.layout.BoxWithConstraints].
 */
val LocalChatBubbleMaxWidth = compositionLocalOf<Dp?> { null }

/** Jump-to-message highlight id; avoids per-row [derivedStateOf] in the lazy list. */
val LocalChatHighlightMessageId = compositionLocalOf<String?> { null }
