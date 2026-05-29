package com.lastasylum.alliance.data.voice

import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.data.chat.ChatTeamVoiceRoom
import com.lastasylum.alliance.di.AppContainer

/**
 * Keeps [TeamVoicePresenceStore] in sync while the overlay «Участники онлайн» panel is open.
 * When the user has no active [com.lastasylum.alliance.di.AppContainer.overlayVoiceSession],
 * joins the team voice room with mic/sound off (receive-only) so peer mic/sound badges match the server.
 */
class VoicePresenceRosterSync(
    private val appContainer: AppContainer,
) {
    private val socketManager = appContainer.voiceSocket
    private var refCount = 0
    private var weOpenedConnection = false

    private val peerListener: (VoicePeerEvent) -> Unit = { event ->
        TeamVoicePresenceStore.apply(event)
    }

    @Synchronized
    fun acquire() {
        refCount++
        if (refCount != 1) return
        if (appContainer.overlayVoiceSession != null) return
        socketManager.addPeerListener(peerListener)
        if (!socketManager.isVoiceJoined()) {
            socketManager.connect(
                baseUrl = BuildConfig.API_BASE_URL,
                roomId = ChatTeamVoiceRoom.SOCKET_ROOM_ID,
                tokenProvider = { appContainer.tokenStore.getAccessToken() },
                initialMicOn = false,
                initialSoundOn = false,
            )
            weOpenedConnection = true
        }
    }

    @Synchronized
    fun release() {
        if (refCount <= 0) return
        refCount--
        if (refCount != 0) return
        socketManager.removePeerListener(peerListener)
        if (weOpenedConnection && appContainer.overlayVoiceSession == null) {
            socketManager.disconnect()
            TeamVoicePresenceStore.clear()
        }
        weOpenedConnection = false
    }
}
