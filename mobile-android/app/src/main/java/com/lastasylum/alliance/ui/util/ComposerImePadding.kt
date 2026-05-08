package com.lastasylum.alliance.ui.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/**
 * Поднимает композер над IME при [android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING]:
 * полный [Modifier.imePadding] даёт лишний зазор, пока видна нижняя панель вкладок (~56dp + navigation bar).
 */
@Composable
fun Modifier.composerImeAboveBottomNav(): Modifier {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val tabPx = with(density) { SquadRelayDimens.bottomNavigationBarHeight.roundToPx() }
    val padPx = (imeBottom - tabPx - navBottom).coerceAtLeast(0)
    val padDp = with(density) { padPx.toDp() }
    return this.padding(bottom = padDp + SquadRelayDimens.keyboardComposerGap)
}
