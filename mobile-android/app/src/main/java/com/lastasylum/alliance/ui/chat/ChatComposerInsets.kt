package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/** Нижний блок Scaffold: высота вкладок + vertical padding (10+10). */
private val bottomNavigationBarBlockHeight =
    SquadRelayDimens.bottomNavigationBarHeight + 20.dp

/**
 * Якорит панель ввода над IME ([adjustNothing]) или над нижней навигацией.
 * Вешать на [Column]/[Box] с [androidx.compose.foundation.layout.BoxScope.align] Bottom.
 */
fun Modifier.chatComposerDock(overlayUi: Boolean): Modifier = composed {
    if (overlayUi) {
        windowInsetsPadding(WindowInsets.ime)
            .padding(bottom = SquadRelayDimens.keyboardComposerGap)
    } else {
        val density = LocalDensity.current
        val imeBottomPx = WindowInsets.ime.getBottom(density)
        val bottomGap = if (imeBottomPx > 0) {
            SquadRelayDimens.keyboardComposerGap
        } else {
            bottomNavigationBarBlockHeight + SquadRelayDimens.chatComposerNavGap
        }
        windowInsetsPadding(WindowInsets.ime)
            .padding(bottom = bottomGap)
    }
}

/** @deprecated Композер в отдельном слое [chatComposerDock]; отступы не на корневом Box. */
fun Modifier.chatComposerHostBottomSpacing(): Modifier = Modifier

/** IME/зазор задаёт [chatComposerDock] на хосте экрана. */
fun Modifier.chatComposerImePadding(overlayUi: Boolean): Modifier = Modifier
