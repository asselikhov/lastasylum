package com.lastasylum.alliance.ui.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/**
 * Оверлей-чат без нижней панели вкладок: отступ снизу = высота IME + [SquadRelayDimens.keyboardComposerGap],
 * без вычитания высоты bottom navigation.
 */
@Composable
fun Modifier.composerImeOverlay(): Modifier {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val padDp = with(density) { imeBottom.toDp() }
    return this.padding(bottom = padDp + SquadRelayDimens.keyboardComposerGap)
}

/**
 * Поднимает композер над IME при [android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING]:
 * полный [Modifier.imePadding] даёт лишний зазор, пока видна нижняя панель вкладок (~56dp + navigation bar).
 */
@Composable
fun Modifier.composerImeAboveBottomNav(): Modifier {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val tabPx = with(density) { SquadRelayDimens.bottomNavigationBarHeight.roundToPx() }
    // Scaffold already applies bottomBar height as content padding. We only need to lift by the IME
    // height above that bar. Do not subtract navigation bars: the app hides legacy navigation and
    // gesture insets can otherwise under-pad and let the IME overlap the composer on some devices.
    val padPx = (imeBottom - tabPx).coerceAtLeast(0)
    val padDp = with(density) { padPx.toDp() }
    return this.padding(bottom = padDp + SquadRelayDimens.keyboardComposerGap)
}
