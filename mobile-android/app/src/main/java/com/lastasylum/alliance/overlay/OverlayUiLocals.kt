package com.lastasylum.alliance.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf

/** True when [ChatScreen] / [TeamScreen] run inside the overlay fullscreen panel (not MainActivity). */
val LocalOverlayUiMode = staticCompositionLocalOf { false }

/** Удерживает usage-гейт, пока открыт sheet/dialog в оверлее (дополнительно к флагу полноэкранной панели). */
@Composable
fun OverlayInteractionSuppressEffect() {
    if (!LocalOverlayUiMode.current) return
    DisposableEffect(Unit) {
        OverlayChatInteractionHold.acquireGameForegroundSuppress()
        onDispose { OverlayChatInteractionHold.releaseGameForegroundSuppress() }
    }
}

/**
 * Оборачивает модальный UI в оверлее подсчётом suppress для game gate.
 * Если перед открытием уже вызван [OverlayChatInteractionHold.prepareOverlayModalInteraction],
 * не дублирует acquire — только удерживает счётчик на время жизни composable.
 */
@Composable
fun OverlayModalScope(
    preparedByCaller: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (!LocalOverlayUiMode.current) {
        content()
        return
    }
    DisposableEffect(preparedByCaller) {
        if (!preparedByCaller) {
            OverlayChatInteractionHold.acquireGameForegroundSuppress()
        }
        onDispose {
            OverlayChatInteractionHold.releaseGameForegroundSuppress()
        }
    }
    content()
}
