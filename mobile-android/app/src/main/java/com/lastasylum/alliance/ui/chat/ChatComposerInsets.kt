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
 * Приложение: подъём только композера над IME (список сообщений не remeasure на каждом кадре анимации).
 * Оверлей по-прежнему поднимает корень [android.widget.FrameLayout].
 */
fun Modifier.chatComposerImePadding(): Modifier = imePadding()

/** @deprecated Используйте [chatComposerImePadding] на [ChatComposerBar], не на весь экран. */
fun Modifier.chatScreenImeLift(): Modifier = chatComposerImePadding()

/** @deprecated IME на уровне композера — [chatComposerImePadding] + [chatComposerOverlayDock]. */
fun Modifier.chatComposerAppDock(): Modifier = chatComposerOverlayDock()

/** @deprecated Используйте [chatComposerOverlayDock] или [chatScreenImeLift]. */
fun Modifier.chatComposerDock(): Modifier = chatComposerOverlayDock()
