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
        OverlayChatInteractionHold.suppressGameForegroundGate = true
        onDispose { OverlayChatInteractionHold.clearSuppressUnlessFullscreenPanel() }
    }
}
