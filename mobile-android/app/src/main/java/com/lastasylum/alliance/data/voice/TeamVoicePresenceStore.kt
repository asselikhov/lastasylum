package com.lastasylum.alliance.data.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Live mic/sound flags for team voice peers (overlay session or roster observer). */
object TeamVoicePresenceStore {
    private val _peers = MutableStateFlow<Map<String, VoicePeerState>>(emptyMap())
    val peers: StateFlow<Map<String, VoicePeerState>> = _peers.asStateFlow()

    fun apply(event: VoicePeerEvent) {
        _peers.update { current ->
            when (event) {
                is VoicePeerEvent.Joined -> current + (event.peer.userId to event.peer)
                is VoicePeerEvent.State -> current + (event.peer.userId to event.peer)
                is VoicePeerEvent.Left -> current - event.userId
            }
        }
    }

    fun setLocal(userId: String, username: String, micOn: Boolean, soundOn: Boolean) {
        if (userId.isBlank()) return
        _peers.update { current ->
            current + (userId to VoicePeerState(userId, username, micOn, soundOn))
        }
    }

    fun clear() {
        _peers.value = emptyMap()
    }

    /** Mic/sound badges for a roster row; self prefers live session flags over stale peer map. */
    fun voiceFlagsForMember(
        memberUserId: String,
        selfUserId: String?,
        peers: Map<String, VoicePeerState>,
        localMicOn: Boolean?,
        localSoundOn: Boolean?,
    ): Pair<Boolean, Boolean> {
        if (!selfUserId.isNullOrBlank() && memberUserId == selfUserId) {
            if (localMicOn != null && localSoundOn != null) {
                return localMicOn to localSoundOn
            }
        }
        val peer = peers[memberUserId]
        return (peer?.micOn == true) to (peer?.soundOn == true)
    }
}
