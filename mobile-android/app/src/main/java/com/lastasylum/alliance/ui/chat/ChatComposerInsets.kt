package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/**
 * Нижний [androidx.compose.material3.Scaffold] content padding (высота [bottomBar]).
 * Задаётся в [com.lastasylum.alliance.ui.AppNavigation].
 */
val LocalScaffoldContentBottomPadding = androidx.compose.runtime.staticCompositionLocalOf { 0.dp }

/**
 * Якорит панель ввода над IME.
 * - Приложение: [offset] по IME (без подъёма всего списка — меньше фризов при закрытии клавиатуры).
 * - Оверлей: только [composerBottomGap] — IME на корневом [android.widget.FrameLayout].
 */
@OptIn(ExperimentalLayoutApi::class)
fun Modifier.chatComposerDock(): Modifier = composed {
    val gap = SquadRelayDimens.composerBottomGap
    if (LocalOverlayUiMode.current) {
        padding(bottom = gap)
    } else {
        val density = LocalDensity.current
        val scaffoldBottomPx = with(density) {
            LocalScaffoldContentBottomPadding.current.roundToPx()
        }
        val imePx = maxOf(
            WindowInsets.ime.getBottom(density),
            WindowInsets.imeAnimationTarget.getBottom(density),
        )
        val liftPx = (imePx - scaffoldBottomPx).coerceAtLeast(0)
        offset { IntOffset(0, -liftPx) }
            .padding(bottom = gap)
    }
}
