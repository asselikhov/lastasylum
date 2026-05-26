package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/**
 * Оверлей (adjustNothing): [imePadding] анимируется системой, + 8dp.
 */
fun Modifier.chatComposerOverlayDock(): Modifier =
    imePadding().padding(bottom = SquadRelayDimens.composerBottomGap)

/**
 * Приложение (adjustResize): окно сжимается под IME — композеру нужен только зазор 8dp.
 * Без чтения [WindowInsets.ime] в Composable (иначе перерисовка всего [ChatScreen] на каждый кадр).
 */
fun Modifier.chatComposerAppDock(): Modifier =
    padding(bottom = SquadRelayDimens.composerBottomGap)

/** @deprecated Используйте [chatComposerOverlayDock] или [chatComposerAppDock]. */
fun Modifier.chatComposerDock(): Modifier = chatComposerAppDock()
