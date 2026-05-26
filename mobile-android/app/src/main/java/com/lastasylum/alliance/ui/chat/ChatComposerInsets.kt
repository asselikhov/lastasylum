package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/**
 * Нижний отступ под полем ввода.
 * IME: в приложении — [androidx.compose.foundation.layout.imePadding] на колонке чата
 * ([com.lastasylum.alliance.ui.screens.ChatScreen]); в оверлее — padding корневого [android.widget.FrameLayout].
 */
fun Modifier.chatComposerDock(): Modifier =
    padding(bottom = SquadRelayDimens.composerBottomGap)
