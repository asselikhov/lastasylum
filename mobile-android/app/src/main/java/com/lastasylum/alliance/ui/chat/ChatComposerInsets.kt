package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/**
 * Оверлей: IME поднимает корень [android.widget.FrameLayout] в [CombatOverlayService];
 * здесь только зазор 8dp над нижним краем контента.
 */
fun Modifier.chatComposerOverlayDock(): Modifier =
    padding(bottom = SquadRelayDimens.composerBottomGap)

/**
 * Приложение (edge-to-edge, adjustNothing): [imePadding] + 8dp над клавиатурой.
 */
fun Modifier.chatComposerAppDock(): Modifier =
    imePadding().padding(bottom = SquadRelayDimens.composerBottomGap)

/** @deprecated Используйте [chatComposerOverlayDock] или [chatComposerAppDock]. */
fun Modifier.chatComposerDock(): Modifier = chatComposerAppDock()
