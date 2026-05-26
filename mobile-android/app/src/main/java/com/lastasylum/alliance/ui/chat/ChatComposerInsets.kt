package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/** Оверлей (adjustNothing): [imePadding] + 8dp. */
fun Modifier.chatComposerOverlayDock(): Modifier =
    imePadding().padding(bottom = SquadRelayDimens.composerBottomGap)

/**
 * Приложение: [imePadding] с компенсацией bottomBar Scaffold.
 * Вызывать только из [ChatComposerBar], чтобы не перерисовывать ленту на каждый кадр IME.
 */
@Composable
fun Modifier.chatComposerAppDock(): Modifier {
    val density = LocalDensity.current
    val gap = SquadRelayDimens.composerBottomGap
    val imePx = WindowInsets.ime.getBottom(density)
    if (imePx <= 0) {
        return padding(bottom = gap)
    }
    val obstructionPx = with(density) {
        SquadRelayDimens.chatComposerScaffoldBottomObstruction.roundToPx()
    }
    val gapPx = with(density) { gap.roundToPx() }
    val bottomPx = (imePx - obstructionPx).coerceAtLeast(0) + gapPx
    return padding(bottom = with(density) { bottomPx.toDp() })
}

/** @deprecated Используйте [chatComposerOverlayDock] или [chatComposerAppDock]. */
@Composable
fun Modifier.chatComposerDock(): Modifier = chatComposerAppDock()
