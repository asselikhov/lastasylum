package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/**
 * Якорит панель ввода над IME ([adjustNothing]) с [SquadRelayDimens.composerBottomGap].
 * В Scaffold контент уже над [bottomBar]: при IME вычитаем высоту bottomBar из inset,
 * иначе зазор над клавиатурой ≈ gap + высота навбара.
 */
fun Modifier.chatComposerDock(): Modifier = composed {
    val density = LocalDensity.current
    val overlayUi = LocalOverlayUiMode.current
    val gap = SquadRelayDimens.composerBottomGap
    val imeBottomPx = WindowInsets.ime.getBottom(density)

    if (overlayUi) {
        windowInsetsPadding(WindowInsets.ime)
            .padding(bottom = gap)
    } else if (imeBottomPx > 0) {
        val bottomBarPx = with(density) {
            SquadRelayDimens.bottomNavigationBarBlockHeight.roundToPx()
        }
        val effectiveImePx = (imeBottomPx - bottomBarPx).coerceAtLeast(0)
        padding(
            bottom = with(density) { effectiveImePx.toDp() } + gap,
        )
    } else {
        padding(bottom = gap)
    }
}
