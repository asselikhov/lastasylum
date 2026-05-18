package com.lastasylum.alliance.ui.team

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.lastasylum.alliance.data.voice.TeamVoicePresenceStore
import com.lastasylum.alliance.di.AppContainer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember

/**
 * Voice peer badges in roster reflect overlay raid voice only (in-game with overlay).
 * No passive socket join from the team screen.
 */
@Composable
fun TeamVoiceRosterPresenceBinding(
    active: Boolean,
    overlayVisible: Boolean,
) {
    if (!active) return
    val context = LocalContext.current
    val app = remember(context) { AppContainer.from(context) }

    DisposableEffect(active, overlayVisible, app.overlayVoiceSession) {
        if (!overlayVisible && app.overlayVoiceSession == null) {
            TeamVoicePresenceStore.clear()
        }
        onDispose {
            if (!overlayVisible && app.overlayVoiceSession == null) {
                TeamVoicePresenceStore.clear()
            }
        }
    }
}
