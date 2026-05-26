package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/** Оверлей (adjustNothing): [imePadding] + 8dp. */
fun Modifier.chatComposerOverlayDock(): Modifier =
    imePadding().padding(bottom = SquadRelayDimens.composerBottomGap)

/**
 * Приложение (adjustResize): окно сжимается под IME — только зазор 8dp.
 * Не читать [androidx.compose.foundation.layout.WindowInsets.ime] (даёт двойной отступ с resize).
 */
fun Modifier.chatComposerAppDock(): Modifier =
    padding(bottom = SquadRelayDimens.composerBottomGap)

/** @deprecated Используйте [chatComposerOverlayDock] или [chatComposerAppDock]. */
fun Modifier.chatComposerDock(): Modifier = chatComposerAppDock()
