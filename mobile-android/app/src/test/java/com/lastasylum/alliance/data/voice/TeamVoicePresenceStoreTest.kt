package com.lastasylum.alliance.data.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamVoicePresenceStoreTest {
    @Test
    fun voiceFlagsForMember_prefersLocalSessionForSelf() {
        val peers = mapOf(
            "self" to VoicePeerState("self", "me", micOn = false, soundOn = false),
        )
        val (mic, sound) = TeamVoicePresenceStore.voiceFlagsForMember(
            memberUserId = "self",
            selfUserId = "self",
            peers = peers,
            localMicOn = true,
            localSoundOn = true,
        )
        assertTrue(mic)
        assertTrue(sound)
    }

    @Test
    fun voiceFlagsForMember_usesPeersForOtherMembers() {
        val peers = mapOf(
            "other" to VoicePeerState("other", "x", micOn = true, soundOn = false),
        )
        val (mic, sound) = TeamVoicePresenceStore.voiceFlagsForMember(
            memberUserId = "other",
            selfUserId = "self",
            peers = peers,
            localMicOn = false,
            localSoundOn = false,
        )
        assertTrue(mic)
        assertFalse(sound)
    }
}
