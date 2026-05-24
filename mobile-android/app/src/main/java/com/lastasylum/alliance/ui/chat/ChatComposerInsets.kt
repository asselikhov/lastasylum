package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/** Зазор между панелью ввода и нижней навигацией (композер в content, навбар в bottomBar). */
fun Modifier.chatComposerHostBottomSpacing(): Modifier = composed {
    padding(bottom = SquadRelayDimens.chatComposerNavGap)
}

/** Оверлей: микрозазор над клавиатурой на корневом FrameLayout. В приложении — adjustResize. */
fun Modifier.chatComposerImePadding(overlayUi: Boolean): Modifier = composed {
    if (overlayUi) {
        padding(bottom = SquadRelayDimens.keyboardComposerGap)
    } else {
        this
    }
}
