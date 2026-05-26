package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lastasylum.alliance.overlay.LocalOverlayUiMode

/**
 * Колонка композера с нижним зазором 8dp.
 * IME: оверлей — padding корня [android.widget.FrameLayout]; приложение — [chatComposerImePadding].
 */
@Composable
internal fun ChatComposerBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val liftWithIme = !LocalOverlayUiMode.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (liftWithIme) Modifier.chatComposerImePadding() else Modifier)
            .chatComposerOverlayDock(),
    ) {
        content()
    }
}
