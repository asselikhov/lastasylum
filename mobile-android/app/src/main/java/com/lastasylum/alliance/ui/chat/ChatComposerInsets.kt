package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import com.lastasylum.alliance.ui.insets.LocalImeSnapState
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/**
 * Оверлей: IME поднимает корень [android.widget.FrameLayout] в [CombatOverlayService];
 * здесь только зазор 8dp над нижним краем контента.
 */
fun Modifier.chatComposerOverlayDock(): Modifier =
    padding(bottom = SquadRelayDimens.composerBottomGap)

/**
 * Приложение (edge-to-edge, adjustNothing): только композер реагирует на IME.
 * На вкладке чата нижняя навигация скрывается при открытой клавиатуре ([com.lastasylum.alliance.ui.AppNavigation]),
 * поэтому достаточно полного IME-inset без вычитания высоты bottomBar.
 */
fun Modifier.chatComposerAppDock(): Modifier = composed {
    val density = LocalDensity.current
    val imeBottomPx = LocalImeSnapState.current.bottomPx
    val bottom = if (imeBottomPx > 0) {
        with(density) { imeBottomPx.toDp() } + SquadRelayDimens.composerBottomGap
    } else {
        SquadRelayDimens.composerBottomGap
    }
    padding(bottom = bottom)
}

/** @deprecated Используйте [chatComposerOverlayDock] или [chatComposerAppDock]. */
fun Modifier.chatComposerDock(): Modifier = chatComposerAppDock()
