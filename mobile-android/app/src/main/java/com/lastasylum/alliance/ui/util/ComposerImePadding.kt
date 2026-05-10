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
