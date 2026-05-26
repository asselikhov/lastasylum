package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/**
 * Оверлей: IME в Compose ([imePadding]); корневой FrameLayout — только systemBars.
 */
fun Modifier.chatComposerOverlayDock(): Modifier =
    imePadding().padding(bottom = SquadRelayDimens.composerBottomGap)

/**
 * Приложение: [imePadding] + компенсация bottomBar Scaffold (навбар не скрываем).
 */
fun Modifier.chatComposerAppDock(): Modifier = composed {
    val density = LocalDensity.current
    val obstructionPx = with(density) {
        SquadRelayDimens.chatComposerScaffoldBottomObstruction.roundToPx()
    }
    val pullDownPx = if (WindowInsets.ime.getBottom(density) > 0) obstructionPx else 0
    Modifier
        .offset { IntOffset(0, pullDownPx) }
        .imePadding()
        .padding(bottom = SquadRelayDimens.composerBottomGap)
}

/** @deprecated Используйте [chatComposerOverlayDock] или [chatComposerAppDock]. */
fun Modifier.chatComposerDock(): Modifier = chatComposerAppDock()
