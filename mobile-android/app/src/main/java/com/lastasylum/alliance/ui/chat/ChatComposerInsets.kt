package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/**
 * Отступ под блоком композера: зазор над нижней навигацией, когда клавиатуры нет.
 * При открытой IME навигация скрыта — дополнительный отступ не нужен.
 */
fun Modifier.chatComposerHostBottomSpacing(): Modifier = composed {
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    padding(
        bottom = if (imeVisible) 0.dp else SquadRelayDimens.chatComposerNavGap,
    )
}

/**
 * Микрозазор над клавиатурой. В основном приложении окно поднимает adjustResize;
 * оверлей: IME на корневом FrameLayout.
 */
fun Modifier.chatComposerImePadding(overlayUi: Boolean): Modifier = composed {
    if (!overlayUi) return@composed this
    padding(bottom = SquadRelayDimens.keyboardComposerGap)
}
