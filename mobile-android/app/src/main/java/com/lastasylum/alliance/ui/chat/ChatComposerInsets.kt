package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
 * Подъём контента над клавиатурой (только основное приложение).
 * В оверлей-чате IME уже на корневом [android.widget.FrameLayout] в [com.lastasylum.alliance.overlay.CombatOverlayService].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberChatImeContentLift(): Dp {
    if (LocalOverlayUiMode.current) return 0.dp
    val density = LocalDensity.current
    val scaffoldBottomPx = with(density) {
        LocalScaffoldContentBottomPadding.current.roundToPx()
    }
    val imeBottomPx = WindowInsets.imeAnimationTarget.getBottom(density)
    return with(density) {
        (imeBottomPx - scaffoldBottomPx).coerceAtLeast(0).toDp()
    }
}

/**
 * Якорит панель ввода над IME.
 * - Приложение: [offset] по [WindowInsets.imeAnimationTarget] + [composerBottomGap].
 * - Оверлей: только [composerBottomGap] — IME padding на корневом View, без дубля в Compose.
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
        val imeBottomPx = WindowInsets.imeAnimationTarget.getBottom(density)
        val liftPx = (imeBottomPx - scaffoldBottomPx).coerceAtLeast(0)
        offset { IntOffset(0, -liftPx) }
            .padding(bottom = gap)
    }
}
