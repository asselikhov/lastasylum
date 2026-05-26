package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.insets.LocalImeSnapState
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/**
 * Оверлей: IME поднимает корень [android.widget.FrameLayout] в [CombatOverlayService];
 * здесь только зазор 8dp над нижним краем контента.
 */
fun Modifier.chatComposerOverlayDock(): Modifier =
    padding(bottom = SquadRelayDimens.composerBottomGap)

/**
 * Приложение: подъём всего [com.lastasylum.alliance.ui.screens.ChatScreen] (как padding корня в оверлее).
 * Композер — только [chatComposerOverlayDock].
 */
fun Modifier.chatScreenImeLift(): Modifier = composed {
    val density = LocalDensity.current
    val imeBottomPx = LocalImeSnapState.current.bottomPx
    val bottom = if (imeBottomPx > 0) {
        with(density) { imeBottomPx.toDp() }
    } else {
        0.dp
    }
    padding(bottom = bottom)
}

/** @deprecated IME на уровне экрана — [chatScreenImeLift] + [chatComposerOverlayDock]. */
fun Modifier.chatComposerAppDock(): Modifier = chatComposerOverlayDock()

/** @deprecated Используйте [chatComposerOverlayDock] или [chatScreenImeLift]. */
fun Modifier.chatComposerDock(): Modifier = chatComposerOverlayDock()
