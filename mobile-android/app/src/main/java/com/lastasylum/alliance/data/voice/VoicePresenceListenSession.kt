package com.lastasylum.alliance.data.voice

import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.data.auth.TokenStore

/**
 * Joins raid voice room without capture/playback — only peer mic/sound state for roster UI.
 * Must not run while [com.lastasylum.alliance.di.AppContainer.overlayVoiceSession] owns the socket.
 */
class VoicePresenceListenSession(
    private val socketManager: VoiceSocketManager,
    private val tokenStore: TokenStore,
    private val isOverlayVoiceActive: () -> Boolean,
) {
    private var started = false
    private val peerListener: (VoicePeerEvent) -> Unit = { event ->
        TeamVoicePresenceStore.apply(event)
    }

    fun start(roomId: String, localUserId: String) {
        if (started || isOverlayVoiceActive()) return
        val rid = roomId.trim()
        if (rid.isEmpty()) return
        started = true
        socketManager.addPeerListener(peerListener)
        socketManager.connect(
            baseUrl = BuildConfig.API_BASE_URL,
            roomId = rid,
            tokenProvider = { tokenStore.getAccessToken() },
        )
        socketManager.emitState(micOn = false, soundOn = false)
        TeamVoicePresenceStore.setLocal(localUserId, "", micOn = false, soundOn = false)
    }

    fun stop() {
        if (!started) return
        started = false
        socketManager.removePeerListener(peerListener)
        if (!isOverlayVoiceActive()) {
            socketManager.disconnect()
        }
    }
}
