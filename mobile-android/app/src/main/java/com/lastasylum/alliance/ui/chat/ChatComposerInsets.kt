package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/**
 * Оверлей: IME поднимает корень [android.widget.FrameLayout] в [CombatOverlayService];
 * здесь только зазор 8dp над нижним краем контента.
 */
fun Modifier.chatComposerOverlayDock(): Modifier =
    padding(bottom = SquadRelayDimens.composerBottomGap)

/**
 * Приложение: подъём всего [com.lastasylum.alliance.ui.screens.ChatScreen] над IME
 * (аналог padding корня в оверлее). Композер — [chatComposerOverlayDock] (+8dp).
 */
fun Modifier.chatScreenImeLift(): Modifier = imePadding()

/** @deprecated IME на уровне экрана — [chatScreenImeLift] + [chatComposerOverlayDock]. */
fun Modifier.chatComposerAppDock(): Modifier = chatComposerOverlayDock()

/** @deprecated Используйте [chatComposerOverlayDock] или [chatScreenImeLift]. */
fun Modifier.chatComposerDock(): Modifier = chatComposerOverlayDock()
