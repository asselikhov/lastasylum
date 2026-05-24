package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/**
 * Нижний [androidx.compose.material3.Scaffold] content padding (высота [bottomBar]).
 * Задаётся в [com.lastasylum.alliance.ui.AppNavigation].
 */
val LocalScaffoldContentBottomPadding = androidx.compose.runtime.staticCompositionLocalOf { 0.dp }

/**
 * Якорит панель ввода над IME ([adjustNothing]) с [SquadRelayDimens.composerBottomGap].
 * В приложении: [consumeWindowInsets] для Scaffold + [imePadding], без ручного вычитания высоты навбара.
 */
fun Modifier.chatComposerDock(): Modifier = composed {
    val overlayUi = LocalOverlayUiMode.current
    val gap = SquadRelayDimens.composerBottomGap
    val scaffoldBottom: Dp = LocalScaffoldContentBottomPadding.current

    if (overlayUi) {
        windowInsetsPadding(WindowInsets.ime)
            .padding(bottom = gap)
    } else {
        consumeWindowInsets(PaddingValues(bottom = scaffoldBottom))
            .imePadding()
            .padding(bottom = gap)
    }
}
