package com.lastasylum.alliance.ui.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/**
 * Поднимает композер над IME при главном приложении: нижняя панель вкладок уже учтена в padding контента
 * Scaffold, поэтому из высоты IME вычитается [SquadRelayDimens.bottomNavigationBarHeight].
 * Нужно при [enableEdgeToEdge]: одного [android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE]
 * часто недостаточно — [WindowInsets.ime] всё равно задаёт подъём.
 */
@Composable
fun Modifier.composerImeAboveBottomNav(): Modifier {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val tabPx = with(density) { SquadRelayDimens.bottomNavigationBarHeight.roundToPx() }
    val padPx = (imeBottom - tabPx).coerceAtLeast(0)
    val padDp = with(density) { padPx.toDp() }
    return this.padding(bottom = padDp + SquadRelayDimens.keyboardComposerGap)
}

/** Полноэкранный чат без нижней панели вкладок (оверлей с [SOFT_INPUT_ADJUST_RESIZE]): полная высота IME + зазор. */
@Composable
fun Modifier.composerImeFullscreenOverlay(): Modifier {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val padDp = with(density) { imeBottom.toDp() }
    return this.padding(bottom = padDp + SquadRelayDimens.keyboardComposerGap)
}
