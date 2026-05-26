package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Колонка композера с нижним зазором 8dp.
 * IME: оверлей — padding корневого [android.widget.FrameLayout]; приложение — [chatScreenImeLift] на [ChatScreen].
 */
@Composable
internal fun ChatComposerBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .chatComposerOverlayDock(),
    ) {
        content()
    }
}
