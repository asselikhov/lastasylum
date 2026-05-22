package com.lastasylum.alliance.ui.chat

import androidx.compose.runtime.staticCompositionLocalOf

/** Открытие галереи вложений по URL (чат комнат и темы форума). */
val LocalOpenRemoteChatImagePreview =
    staticCompositionLocalOf<(List<String>, Int) -> Unit> { { _, _ -> } }
