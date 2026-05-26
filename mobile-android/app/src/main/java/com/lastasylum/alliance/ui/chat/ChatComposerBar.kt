package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lastasylum.alliance.overlay.LocalOverlayUiMode

/**
 * Колонка композера с IME-dock. Приложение: только эта ветка пересчитывается при анимации клавиатуры,
 * лента в [com.lastasylum.alliance.ui.screens.ChatScreen] — нет.
 */
@Composable
internal fun ChatComposerBar(
    overlayUi: Boolean = LocalOverlayUiMode.current,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dockModifier = if (overlayUi) {
        Modifier.chatComposerOverlayDock()
    } else {
        Modifier.chatComposerAppDock()
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(dockModifier),
    ) {
        content()
    }
}
